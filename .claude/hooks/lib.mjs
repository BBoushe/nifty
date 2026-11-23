import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

/**
 * Read JSON payload from stdin (Claude Code PreToolUse / PostToolUse hook format).
 * Returns `{}` on empty or non-JSON input so hooks never crash unexpectedly.
 */
export function readStdinJson() {
  try {
    const raw = fs.readFileSync(0, 'utf8').trim();
    if (!raw) return {};
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

/**
 * Normalize a file path to forward slashes without leading `./`.
 */
export function normalizePath(input) {
  if (typeof input !== 'string') return '';
  return input
    .trim()
    .replaceAll('\\', '/')
    .replace(/^\.\//, '')
    .replace(/\/+/g, '/');
}

/**
 * Resolve git repository root from cwd (falls back to cwd).
 */
export function getRepoRoot(cwd = process.cwd()) {
  try {
    const out = execFileSync('git', ['rev-parse', '--show-toplevel'], {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
    return out || cwd;
  } catch {
    return cwd;
  }
}

/**
 * Normalize an absolute or relative path into a repo-relative POSIX path when possible.
 */
export function toRepoRelative(input, repoRoot = process.cwd()) {
  const norm = normalizePath(input);
  if (!norm) return '';
  const rootNorm = normalizePath(path.resolve(repoRoot)).replace(/\/$/, '');
  if (norm.startsWith(`${rootNorm}/`)) {
    return norm.slice(rootNorm.length + 1);
  }
  if (path.isAbsolute(input)) {
    const rel = normalizePath(path.relative(repoRoot, input));
    if (!rel.startsWith('..')) return rel;
  }
  return norm;
}

/**
 * Returns true if a file path or glob pattern targets `.env` or `.env.*`
 * while explicitly allowing `.env.example` (and `*.env.example`).
 */
export function isBlockedEnvPath(input) {
  if (typeof input !== 'string' || !input.trim()) return false;
  const cleaned = normalizePath(input).replace(/^['"]|['"]$/g, '');
  if (!cleaned) return false;

  const segments = cleaned.split('/').filter(Boolean);
  for (const seg of segments) {
    const lower = seg.toLowerCase();
    if (lower === '.env.example' || lower.endsWith('.env.example')) {
      continue;
    }
    // Exact .env, .env.<anything>, or glob targeting .env* (e.g. .env*, .env.?, *.env)
    if (/^\.env($|\.|[*?\[])/.test(lower) || /\*\.env($|\.)/.test(lower)) {
      return true;
    }
  }
  return false;
}

/**
 * Split a shell command string into top-level segments separated by unquoted
 * `&&`, `||`, `;`, `|`, or newlines.
 */
export function splitShellSegments(command) {
  if (typeof command !== 'string' || !command.trim()) return [];
  const segments = [];
  let current = '';
  let inSingle = false;
  let inDouble = false;
  let escaped = false;

  for (let i = 0; i < command.length; i += 1) {
    const ch = command[i];
    const next = command[i + 1] ?? '';

    if (escaped) {
      current += ch;
      escaped = false;
      continue;
    }

    if (ch === '\\' && !inSingle) {
      current += ch;
      escaped = true;
      continue;
    }

    if (ch === "'" && !inDouble) {
      inSingle = !inSingle;
      current += ch;
      continue;
    }

    if (ch === '"' && !inSingle) {
      inDouble = !inDouble;
      current += ch;
      continue;
    }

    if (!inSingle && !inDouble) {
      if ((ch === '&' && next === '&') || (ch === '|' && next === '|')) {
        if (current.trim()) segments.push(current.trim());
        current = '';
        i += 1;
        continue;
      }
      if (ch === ';' || ch === '|' || ch === '\n') {
        if (current.trim()) segments.push(current.trim());
        current = '';
        continue;
      }
    }

    current += ch;
  }

  if (current.trim()) segments.push(current.trim());
  return segments;
}

/**
 * Tokenize a single shell segment respecting single and double quotes.
 * Each token keeps `{ value, quoted }` so message flags can be distinguished cleanly.
 */
export function tokenizeSegment(segment) {
  const tokens = [];
  let current = '';
  let inSingle = false;
  let inDouble = false;
  let escaped = false;
  let quoted = false;
  let hasToken = false;

  for (let i = 0; i < segment.length; i += 1) {
    const ch = segment[i];
    if (escaped) {
      current += ch;
      escaped = false;
      hasToken = true;
      continue;
    }
    if (ch === '\\' && !inSingle) {
      escaped = true;
      hasToken = true;
      continue;
    }
    if (ch === "'" && !inDouble) {
      inSingle = !inSingle;
      quoted = true;
      hasToken = true;
      continue;
    }
    if (ch === '"' && !inSingle) {
      inDouble = !inDouble;
      quoted = true;
      hasToken = true;
      continue;
    }
    if (!inSingle && !inDouble && /\s/.test(ch)) {
      if (hasToken) {
        tokens.push({ value: current, quoted });
        current = '';
        quoted = false;
        hasToken = false;
      }
      continue;
    }
    current += ch;
    hasToken = true;
  }

  if (hasToken) {
    tokens.push({ value: current, quoted });
  }
  return tokens;
}

const PLACEHOLDER_VALUE_RE =
  /^(|not-configured|changeme|placeholder|example|dummy|sample|none|null|xxx+|your[-_].*|<[^>]+>|\$\{[^}]+\}|\$[A-Z0-9_]+)$/i;

const SAFE_DEV_DB_PASSWORDS = new Set([
  'nifty',
  'postgres',
  'password',
  'changeme',
  'test',
  'local',
  'dev',
]);

export const SECRET_PATTERNS = [
  { name: 'Anthropic API key', regex: /\bsk-ant-[A-Za-z0-9_-]{16,}\b/ },
  { name: 'OpenAI / sk-* API key', regex: /\bsk-(?!ant-)[A-Za-z0-9_-]{20,}\b/ },
  { name: 'Google API key', regex: /\bAIza[0-9A-Za-z_-]{30,}\b/ },
  { name: 'GitHub PAT (ghp_)', regex: /\bghp_[A-Za-z0-9]{20,}\b/ },
  { name: 'GitHub fine-grained PAT', regex: /\bgithub_pat_[A-Za-z0-9_]{20,}\b/ },
  { name: 'Private key block', regex: /-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----/ },
];

/**
 * Scan added lines in a unified diff (or plain text if `rawMode` is true)
 * for leaked API keys, private keys, or non-placeholder credentials.
 */
export function findSecretsInDiff(diffText, { rawMode = false } = {}) {
  if (typeof diffText !== 'string' || !diffText) return [];
  const findings = [];
  const lines = diffText.split(/\r?\n/);

  for (const rawLine of lines) {
    let line = rawLine;
    if (!rawMode) {
      if (!rawLine.startsWith('+') || rawLine.startsWith('+++')) continue;
      line = rawLine.slice(1);
    }

    for (const pattern of SECRET_PATTERNS) {
      if (pattern.regex.test(line)) {
        findings.push({ name: pattern.name, preview: redactLine(line) });
      }
    }

    // Non-placeholder KICONNECT_API_KEY assignment
    const kiMatch = line.match(/\bKICONNECT_API_KEY\s*[:=]\s*["'`]?([^\s"'`#]+)["'`]?/);
    if (kiMatch) {
      const val = kiMatch[1].trim().replace(/[,;.)]+$/, '');
      if (!PLACEHOLDER_VALUE_RE.test(val) && val.length >= 8) {
        findings.push({ name: 'Non-placeholder KICONNECT_API_KEY', preview: redactLine(line) });
      }
    }

    // Non-placeholder JDBC password in URL or SPRING_DATASOURCE_PASSWORD
    const dsPassMatch = line.match(
      /\b(?:SPRING_DATASOURCE_PASSWORD|POSTGRES_PASSWORD)\s*[:=]\s*["'`]?([^\s"'`#]+)["'`]?/
    );
    if (dsPassMatch) {
      const val = dsPassMatch[1].trim().replace(/[,;.)]+$/, '');
      if (
        !PLACEHOLDER_VALUE_RE.test(val) &&
        !SAFE_DEV_DB_PASSWORDS.has(val.toLowerCase()) &&
        val.length >= 10
      ) {
        findings.push({ name: 'Hardcoded database password', preview: redactLine(line) });
      }
    }

    const jdbcUrlMatch = line.match(/jdbc:postgresql:\/\/[^/\s:@]+:([^@\s/]+)@/i);
    if (jdbcUrlMatch) {
      const val = jdbcUrlMatch[1].trim();
      if (
        !PLACEHOLDER_VALUE_RE.test(val) &&
        !SAFE_DEV_DB_PASSWORDS.has(val.toLowerCase())
      ) {
        findings.push({ name: 'Hardcoded JDBC password', preview: redactLine(line) });
      }
    }
  }

  return findings;
}

function redactLine(line) {
  const trimmed = line.trim();
  if (trimmed.length <= 24) return `${trimmed.slice(0, 8)}...[REDACTED]`;
  return `${trimmed.slice(0, 24)}...[REDACTED]`;
}
