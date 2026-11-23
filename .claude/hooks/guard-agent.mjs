#!/usr/bin/env node
// Role-scoped agent guard (cross-platform Node ESM, zero dependencies).
//
// Modes:
//  - `readonly`   (for cold `reviewer`): blocks all file edits/writes and mutating bash commands.
//  - `tests-only` (for independent `test-writer`): restricts file writes strictly to
//                 `backend/src/test/**` and `frontend/src/**/*.test.*` / `frontend/src/**/*.spec.*`.
//
// Usage:
//   node .claude/hooks/guard-agent.mjs --mode readonly
//   node .claude/hooks/guard-agent.mjs --mode tests-only
import { pathToFileURL } from 'node:url';
import {
  isBlockedEnvPath,
  readStdinJson,
  splitShellSegments,
  tokenizeSegment,
  toRepoRelative,
} from './lib.mjs';

const WRITE_TOOLS = new Set(['Edit', 'Write', 'MultiEdit', 'NotebookEdit']);

const MUTATING_COMMANDS = new Set([
  'rm',
  'mv',
  'cp',
  'mkdir',
  'rmdir',
  'touch',
  'chmod',
  'chown',
  'tee',
  'truncate',
  'dd',
]);

const MUTATING_GIT_SUBCOMMANDS = new Set([
  'add',
  'commit',
  'push',
  'checkout',
  'switch',
  'reset',
  'clean',
  'merge',
  'rebase',
  'cherry-pick',
  'revert',
  'stash',
  'tag',
  'apply',
  'am',
]);

export function isAllowedTestPath(inputPath, repoRoot = process.cwd()) {
  const rel = toRepoRelative(inputPath, repoRoot);
  if (!rel || isBlockedEnvPath(rel)) return false;

  if (/^backend\/src\/test\/.+/.test(rel)) {
    return true;
  }
  if (/^frontend\/src\/.+\.(test|spec)\.[A-Za-z0-9]+$/.test(rel)) {
    return true;
  }
  return false;
}

function isMutatingBashCommand(command) {
  const segments = splitShellSegments(command);
  for (const segment of segments) {
    // Output redirection (allow harmless >/dev/null or 2>/dev/null)
    const strippedNull = segment.replace(/\b[0-9]*>>?\s*\/dev\/null\b/g, '');
    if (/(^|[^<>])>>?(?!&)/.test(strippedNull)) {
      return `output redirection in '${segment}'`;
    }

    const tokens = tokenizeSegment(segment);
    if (!tokens.length) continue;

    // Skip env var prefixes
    let idx = 0;
    while (idx < tokens.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[idx].value)) {
      idx += 1;
    }
    if (idx >= tokens.length) continue;

    const cmd = tokens[idx].value.split(/[\\/]/).pop();
    const rest = tokens.slice(idx + 1).map((t) => t.value);

    if (MUTATING_COMMANDS.has(cmd)) {
      return `mutating command '${cmd}'`;
    }
    if ((cmd === 'sed' || cmd === 'perl') && rest.some((a) => a === '-i' || a.startsWith('-i'))) {
      return `in-place edit '${cmd} -i'`;
    }
    if (cmd === 'git') {
      const sub = rest.find((a) => !a.startsWith('-'));
      if (sub && MUTATING_GIT_SUBCOMMANDS.has(sub)) {
        return `mutating git subcommand 'git ${sub}'`;
      }
    }
    if (cmd === 'npm' && rest[0] && ['install', 'i', 'ci', 'uninstall', 'update'].includes(rest[0])) {
      return `mutating npm command 'npm ${rest[0]}'`;
    }
  }
  return null;
}

export function evaluateAgentAction(payload, mode, options = {}) {
  const repoRoot = options.repoRoot ?? process.cwd();
  const toolName = payload?.tool_name ?? '';
  const input = payload?.tool_input ?? payload ?? {};

  if (mode === 'readonly') {
    if (WRITE_TOOLS.has(toolName)) {
      return {
        blocked: true,
        reason: `BLOCKED (readonly mode): Tool '${toolName}' is not permitted for a cold read-only reviewer.`,
      };
    }
    if (toolName === 'Bash') {
      const command = input.command ?? '';
      const mutation = isMutatingBashCommand(command);
      if (mutation) {
        return {
          blocked: true,
          reason: `BLOCKED (readonly mode): Read-only reviewer cannot run ${mutation}.`,
        };
      }
    }
    return { blocked: false };
  }

  if (mode === 'tests-only') {
    if (WRITE_TOOLS.has(toolName)) {
      const paths = [input.file_path, input.notebook_path, input.path].filter(
        (v) => typeof v === 'string' && v.trim()
      );
      if (Array.isArray(input.edits)) {
        for (const e of input.edits) {
          if (typeof e?.file_path === 'string') paths.push(e.file_path);
        }
      }
      for (const p of paths) {
        if (!isAllowedTestPath(p, repoRoot)) {
          return {
            blocked: true,
            reason:
              `BLOCKED (tests-only mode): '${p}' is outside allowed test paths ` +
              `(backend/src/test/** and frontend/src/**/*.test.* / **/*.spec.*).`,
          };
        }
      }
    }
    return { blocked: false };
  }

  return {
    blocked: true,
    reason: `BLOCKED: Unknown guard-agent mode '${mode}'. Expected 'readonly' or 'tests-only'.`,
  };
}

function parseMode(argv) {
  const idx = argv.indexOf('--mode');
  if (idx !== -1 && argv[idx + 1]) return argv[idx + 1];
  const eq = argv.find((a) => a.startsWith('--mode='));
  if (eq) return eq.slice('--mode='.length);
  return process.env.NIFTY_AGENT_MODE ?? '';
}

function main() {
  const mode = parseMode(process.argv.slice(2));
  const payload = readStdinJson();
  const result = evaluateAgentAction(payload, mode);
  if (result.blocked) {
    process.stderr.write(`${result.reason}\n`);
    process.exit(2);
  }
  process.exit(0);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
