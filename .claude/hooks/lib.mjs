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
 * Normalize an absolute or relative path into a canonical repo-relative POSIX path.
 * Resolves `..` segments and returns `''` if the path escapes `repoRoot`.
 */
export function toRepoRelative(input, repoRoot = process.cwd()) {
  if (typeof input !== 'string' || !input.trim()) return '';
  const cleaned = input.trim().replaceAll('\\', '/');
  const absRoot = path.resolve(repoRoot);
  const resolved = path.resolve(absRoot, cleaned);
  const rel = normalizePath(path.relative(absRoot, resolved));
  if (!rel || rel === '..' || rel.startsWith('../') || path.isAbsolute(rel)) {
    return '';
  }
  return rel;
}

// Wrapper basename -> its flags that consume the next token; every other `-…` flag is skipped alone.
const WRAPPER_VALUE_FLAGS = {
  env: ['-u', '--unset', '-C', '--chdir'],
  command: [],
  nice: ['-n', '--adjustment'],
  time: [],
  sudo: ['-u', '-g', '-C', '-h', '-p', '-r', '-t', '-U', '-D'],
  xargs: ['-I', '-n', '-P', '-L', '-s', '-d', '-E', '-a'],
  exec: ['-a'],
  nohup: [],
  timeout: ['-s', '--signal', '-k', '--kill-after'], // then one duration operand
};
// Shell grouping tokens and keywords that only introduce the command after them.
const SHELL_PREFIX_WORDS = new Set(['(', '{', '!', 'if', 'then', 'else', 'elif', 'do', 'while', 'until']);
// `proxy` is the deprecated alias of `bypass`; all of these run the command that follows.
const RTK_PASSTHROUGH = ['proxy', 'bypass', 'err', 'test', 'summary'];

/**
 * If `value` is a command-string flag of `wrapper` (`'env'`, `'rtk-run'` or `'any'`), return
 * `{ inline }`: the string attached to the flag, or null when the string is the next token.
 * Otherwise return null. The one place that lists these flags (`env -S`, `rtk run -c`).
 */
export function commandStringFlag(value, wrapper = 'any') {
  const forms = [];
  if (wrapper !== 'rtk-run') forms.push(/^--split-string(?:=(.*))?$/s, /^-[iv0]*S(.*)$/s);
  if (wrapper !== 'env') forms.push(/^--command(?:=(.*))?$/s, /^-c(.*)$/s);
  for (const re of forms) {
    const m = re.exec(value);
    if (m) return { inline: m[1] || null };
  }
  return null;
}

function skipFlags(tokens, idx, valueFlags, isStop = () => false) {
  while (idx < tokens.length) {
    const v = tokens[idx].value;
    if (!v.startsWith('-') || isStop(v)) break;
    idx += valueFlags.includes(v) ? 2 : 1;
  }
  return idx;
}

/**
 * Strip leading env assignments (`CI=true npm run build`), shell group/keyword prefixes (`(`, `{`, `if`, `!`, …)
 * and pass-through wrappers (`env`, `command`, `nice`, `time`, `sudo`, `xargs`, `exec`, `timeout`, `nohup`,
 * `rtk [flags] [proxy|bypass|err|test|summary|run]`) in any order, so every guard sees the real command.
 * RTK's PreToolUse hook rewrites `git ...` to `rtk git ...`. Peeling stops on a command-string flag
 * (`commandStringFlag`) and leaves it first, so guard-bash can evaluate the string.
 * See openspec/changes/archive/*-bash-guard-hardening/design.md and *-bash-guard-gaps/design.md.
 * shortcut: interpreter one-liners (python -c, node -e), find -exec, and scripts run by path are not inspected; extend extractCommandString in guard-bash.mjs if agents start using them.
 */
export function stripLeadingEnvAssignments(tokens) {
  let idx = 0;
  for (;;) {
    const t = idx < tokens.length ? tokens[idx].value : '';
    const name = t.split('/').pop();
    if (/^[A-Za-z_][A-Za-z0-9_]*=/.test(t) || (!tokens[idx]?.quoted && SHELL_PREFIX_WORDS.has(t))) idx += 1;
    else if (!tokens[idx]?.quoted && /^[({]+./.test(t)) {
      // `(git push ...)`: drop the group opener stuck to the command and look again.
      tokens = [...tokens.slice(0, idx), { ...tokens[idx], value: t.replace(/^[({]+/, '') }, ...tokens.slice(idx + 1)];
    } else if (Object.hasOwn(WRAPPER_VALUE_FLAGS, name)) {
      const stop = name === 'env' ? (v) => commandStringFlag(v, 'env') : undefined;
      idx = skipFlags(tokens, idx + 1, WRAPPER_VALUE_FLAGS[name], stop);
      if (name === 'timeout') idx += 1;
    } else if (name === 'rtk') {
      idx = skipFlags(tokens, idx + 1, []);
      const sub = tokens[idx]?.value;
      if (RTK_PASSTHROUGH.includes(sub)) idx += 1;
      else if (sub === 'run') idx = skipFlags(tokens, idx + 1, ['--shell'], (v) => commandStringFlag(v, 'rtk-run'));
    } else break;
  }
  const out = tokens.slice(idx);
  const last = out[out.length - 1];
  // `(git push origin master)`: an unquoted trailing `)` closes the group, it is not part of the argument.
  if (last && !last.quoted && /[^)]\)+$/.test(last.value)) {
    out[out.length - 1] = { ...last, value: last.value.replace(/\)+$/, '') };
  }
  return out;
}

/**
 * Extract `{ subcommand, args, globals }` from tokenized git command, skipping global
 * flags that take a separate value (`-C`, `-c`, `--git-dir`, `--work-tree`, `--namespace`).
 * `globals` holds the tokens between `git` and the subcommand (for `-c core.hooksPath=…`).
 */
export function getGitSubcommand(tokens) {
  const cleaned = stripLeadingEnvAssignments(tokens);
  if (!cleaned.length) return null;
  const cmd = cleaned[0].value;
  if (cmd !== 'git' && !cmd.endsWith('/git') && !cmd.endsWith('\\git.exe')) {
    return null;
  }
  let i = 1;
  while (i < cleaned.length) {
    const tok = cleaned[i].value;
    if (
      tok === '-C' ||
      tok === '-c' ||
      tok === '--git-dir' ||
      tok === '--work-tree' ||
      tok === '--namespace' ||
      tok === '--super-prefix'
    ) {
      i += 2;
      continue;
    }
    if (tok.startsWith('-')) {
      i += 1;
      continue;
    }
    return { subcommand: tok, args: cleaned.slice(i + 1), globals: cleaned.slice(1, i) };
  }
  return null;
}

/**
 * True when an unquoted glob argument (`.en?`, `.e*`, `.[e]nv`, `.*`) would match a secret env file.
 * The example file stays allowed.
 */
export function globMatchesEnv(input) {
  if (typeof input !== 'string' || !/[*?[]/.test(input) || /\.env\.example$/i.test(input)) return false;
  return input.split('/').some((seg) => {
    // Only a literal leading `.` can match a dotfile (default shell globbing).
    if (!seg.startsWith('.') || !/[*?[]/.test(seg)) return false;
    const body = seg
      .replace(/[.+^${}()|\\]/g, '\\$&')
      .replace(/\*/g, '[^/]*')
      .replace(/\?/g, '[^/]')
      .replace(/\[!/g, '[^')
      .replace(/\[\\\^/g, '[^') // `[^x]` was escaped to `[\^x]` above
      .replace(/\[:[a-z]+:\]/g, '\\s\\S');
    try {
      const re = new RegExp(`^${body}$`, 'i');
      return re.test('.env') || re.test('.env.local');
    } catch {
      return false; // not a valid glob class, so the shell passes it literally
    }
  });
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

    // bash ends a backtick substitution at the next unescaped backtick, whatever quotes are inside it.
    if (ch === '`' && !inSingle) {
      let j = i + 1;
      while (j < command.length && command[j] !== '`') j += command[j] === '\\' ? 2 : 1;
      current += command.slice(i, j + 1);
      i = j;
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
      // A single `&` runs the left side in the background; `2>&1`, `>&2`, `&>` are redirections.
      const prev = command[i - 1] ?? '';
      if (ch === '&' && prev !== '>' && prev !== '<' && next !== '>') {
        if (current.trim()) segments.push(current.trim());
        current = '';
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

// The only commands whose quoted heredoc text the guard treats as data, never as code.
const TEXT_COMMAND_RE = /^\s*(?:rtk\s+)?(?:git|gh|glab)\s/;
// Flags whose value is message text in git/gh/glab.
const TEXT_FLAG_RE = /(\s(?:-m|-F|--message|--body|--title|--description|--notes)[=\s]+)"\$\(cat <<-?(['"])(\w+)\2\n([\s\S]*?)\n\3\n\)"/g;

/** Quote state at the end of `text`: true when it ends outside quotes and has no substitution. */
function isPlainPrefix(text) {
  if (/\$\(|`/.test(text)) return false;
  let inSingle = false;
  let inDouble = false;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '\\' && !inSingle) i += 1;
    else if (ch === "'" && !inDouble) inSingle = !inSingle;
    else if (ch === '"' && !inSingle) inDouble = !inDouble;
    else if (!inSingle && !inDouble && /[;&|\n#<>()]/.test(ch)) return false;
  }
  return !inSingle && !inDouble;
}

/**
 * Allow-list of heredocs that only carry message text (Decision: bash-guard-gaps design §6):
 *  - `git|gh|glab … -m|-F|--message|--body|--title|--description|--notes "$(cat <<'X'⏎…⏎X⏎)"`,
 *    when everything before it in the command is plain arguments of that one command;
 *  - `git|gh|glab … <<'X'` as the whole first line (`git commit -F - <<'EOF'`).
 * Returns `{ text, bodies }`: `text` has those bodies removed; callers still scan `bodies` as data.
 * Every other heredoc stays in place, so its lines are checked as commands.
 */
export function stripTextHeredocs(command) {
  const bodies = [];
  let text = command;

  const first = /^(\s*(?:rtk\s+)?(?:git|gh|glab)\s[^;&|`$()<>\n#]*<<(-?)(['"])(\w+)\3[ \t]*)\n/.exec(text);
  if (first) {
    const [, head, dash, , delim] = first;
    const lines = text.slice(first[0].length).split('\n');
    const end = lines.findIndex((l) => (dash ? l.replace(/^\t+/, '') : l) === delim);
    if (end !== -1) {
      bodies.push(lines.slice(0, end).join('\n'));
      text = `${head}\n${lines.slice(end).join('\n')}`;
    }
  }

  if (TEXT_COMMAND_RE.test(text)) {
    let out = '';
    let pos = 0;
    let m;
    TEXT_FLAG_RE.lastIndex = 0;
    while ((m = TEXT_FLAG_RE.exec(text)) !== null) {
      const prefix = out + text.slice(pos, m.index) + m[1];
      const [, , , delim, body] = m;
      // A line equal to the delimiter ends the heredoc early in bash: the rest would run as code.
      if (!isPlainPrefix(prefix) || body.split('\n').includes(delim)) break;
      bodies.push(body);
      out = `${prefix}"heredoc-text"`;
      pos = TEXT_FLAG_RE.lastIndex;
    }
    text = out + text.slice(pos);
  }
  return { text, bodies };
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
  let globUnquoted = false;
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
        tokens.push({ value: current, quoted, globUnquoted });
        current = '';
        quoted = false;
        globUnquoted = false;
        hasToken = false;
      }
      continue;
    }
    if (!inSingle && !inDouble && (ch === '*' || ch === '?' || ch === '[')) globUnquoted = true;
    current += ch;
    hasToken = true;
  }

  if (hasToken) {
    tokens.push({ value: current, quoted, globUnquoted });
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
  const varMatch = line.trim().match(/^([A-Za-z_][A-Za-z0-9_]*)\s*[:=]/);
  return varMatch ? `${varMatch[1]}=[REDACTED]` : '[REDACTED]';
}
