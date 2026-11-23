#!/usr/bin/env node
/**
 * Cross-platform Bash & Git pre-commit guard (zero-dependency Node ESM).
 *
 * Modes:
 *  - Claude Code PreToolUse (stdin JSON): blocks protected-branch commits/pushes,
 *    `.env` shell access, `git add && git commit` chaining, and staged secret leaks.
 *  - Git hook (`--pre-commit`): blocks commits on master/main/alpha, staged `.env*`
 *    (except `.env.example`), and secrets in `git diff --cached -U0`.
 */
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import {
  findSecretsInDiff,
  getGitSubcommand,
  isBlockedEnvPath,
  readStdinJson,
  splitShellSegments,
  stripLeadingEnvAssignments,
  tokenizeSegment,
} from './lib.mjs';

const PROTECTED_NO_DIRECT = new Set(['master', 'main', 'alpha']);
const DEV_ALLOWED_COMMIT_RE = /^(docs|hotfix)(\([a-z0-9._/-]+\))?!?: /;

function defaultRunGit(args, cwd = process.cwd()) {
  try {
    return execFileSync('git', args, {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    });
  } catch {
    return '';
  }
}

function extractCommandSubstitutions(segment) {
  const subs = [];
  // Extract $( ... ) substitutions respecting nested parentheses
  for (let i = 0; i < segment.length - 1; i += 1) {
    if (segment[i] === '$' && segment[i + 1] === '(') {
      let depth = 1;
      let j = i + 2;
      while (j < segment.length && depth > 0) {
        if (segment[j] === '(') depth += 1;
        else if (segment[j] === ')') depth -= 1;
        j += 1;
      }
      if (depth === 0) {
        subs.push(segment.slice(i + 2, j - 1));
        i = j - 1;
      }
    }
  }
  // Extract backtick `...` substitutions
  const backtickRe = /`([^`]+)`/g;
  let m;
  while ((m = backtickRe.exec(segment)) !== null) {
    subs.push(m[1]);
  }
  return subs;
}

function segmentTouchesBlockedEnv(segment, depth = 0) {
  if (depth > 4) return null;

  // Check raw input redirections like `< .env`, `<.env`, or `$(<.env)`
  const redirRe = /<\s*([^\s;&|)"'`]+)/g;
  let redirMatch;
  while ((redirMatch = redirRe.exec(segment)) !== null) {
    if (isBlockedEnvPath(redirMatch[1])) {
      return redirMatch[1];
    }
  }

  // Recursively inspect $(...) and `...` subshells even inside echo/printf
  for (const sub of extractCommandSubstitutions(segment)) {
    for (const inner of splitShellSegments(sub)) {
      const hit = segmentTouchesBlockedEnv(inner, depth + 1);
      if (hit) return hit;
    }
  }

  const tokens = stripLeadingEnvAssignments(tokenizeSegment(segment));
  if (!tokens.length) return null;

  const gitInfo = getGitSubcommand(tokens);
  if (gitInfo) {
    // Never flag commit message text like `git commit -m "block .env"`
    if (gitInfo.subcommand === 'commit') return null;
    if (gitInfo.subcommand === 'check-ignore') return null;

    for (const tok of gitInfo.args) {
      if (tok.value.startsWith('-')) continue;
      if (isBlockedEnvPath(tok.value)) return tok.value;
    }
    return null;
  }

  const cmd = tokens[0].value.split(/[\\/]/).pop();
  if (cmd === 'echo' || cmd === 'printf') {
    // Subshells and `<` redirections were already inspected above; plain text arguments are safe.
    return null;
  }

  for (let i = 1; i < tokens.length; i += 1) {
    const val = tokens[i].value;
    if (val.startsWith('-')) {
      // Handle --file=.env or -f=.env
      const eq = val.indexOf('=');
      if (eq !== -1 && isBlockedEnvPath(val.slice(eq + 1))) {
        return val.slice(eq + 1);
      }
      continue;
    }
    if (isBlockedEnvPath(val)) {
      return val;
    }
  }
  return null;
}

function parseGitPushTargets(args, currentBranch) {
  let hasForce = false;
  let hasAllOrMirror = false;
  const positional = [];

  for (let i = 0; i < args.length; i += 1) {
    const val = args[i].value;
    if (val === '-f' || val === '--force' || val === '--force-with-lease') {
      hasForce = true;
      continue;
    }
    if (val === '--all' || val === '--mirror') {
      hasAllOrMirror = true;
      continue;
    }
    if (val === '--repo' || val === '-o' || val === '--push-option' || val === '--receive-pack') {
      i += 1;
      continue;
    }
    if (val.startsWith('-')) continue;
    positional.push(val);
  }

  // positional: [] -> bare push; [remote] -> push currentBranch; [remote, refspec...]
  const refspecs = positional.length >= 2 ? positional.slice(1) : [];
  const targetBranches = [];

  if (refspecs.length === 0) {
    if (currentBranch) targetBranches.push(currentBranch);
  } else {
    for (const spec of refspecs) {
      const cleaned = spec.replace(/^\+/, '');
      const dst = cleaned.includes(':') ? cleaned.split(':').pop() : cleaned;
      const branch = dst.replace(/^refs\/heads\//, '');
      if (branch) targetBranches.push(branch);
    }
  }

  return { hasForce, hasAllOrMirror, targetBranches };
}

function checkStagedChanges({ runGit, stagedDiff, stagedFiles }) {
  const filesOutput =
    typeof stagedFiles === 'string'
      ? stagedFiles
      : Array.isArray(stagedFiles)
        ? stagedFiles.join('\n')
        : runGit(['diff', '--cached', '--name-only']);

  const files = filesOutput
    .split(/\r?\n/)
    .map((f) => f.trim())
    .filter(Boolean);

  for (const file of files) {
    if (isBlockedEnvPath(file)) {
      return {
        blocked: true,
        reason: `BLOCKED: Staged secret environment file '${file}' cannot be committed. Unstage it with 'git reset HEAD ${file}'.`,
      };
    }
  }

  const diff =
    typeof stagedDiff === 'string' ? stagedDiff : runGit(['diff', '--cached', '-U0']);
  const secrets = findSecretsInDiff(diff);
  if (secrets.length > 0) {
    const list = secrets.map((s) => `  - ${s.name}: ${s.preview}`).join('\n');
    return {
      blocked: true,
      reason: `BLOCKED: Potential secret(s) detected in staged diff:\n${list}\nRemove secrets and use .env.example placeholders instead.`,
    };
  }

  return { blocked: false };
}

function checkDevPushCommits(runGit, providedSubjects) {
  let subjects = providedSubjects;
  if (!Array.isArray(subjects)) {
    let range = 'origin/dev..HEAD';
    const hasOriginDev = runGit(['rev-parse', '--verify', '--quiet', 'origin/dev']).trim();
    if (!hasOriginDev) {
      const hasOriginMaster = runGit(['rev-parse', '--verify', '--quiet', 'origin/master']).trim();
      range = hasOriginMaster ? 'origin/master..HEAD' : 'HEAD';
    }
    const raw = runGit(['log', '--format=%s', range]).trim();
    subjects = raw ? raw.split(/\r?\n/).filter(Boolean) : [];
  }

  const invalid = subjects.filter((s) => !DEV_ALLOWED_COMMIT_RE.test(s));
  if (invalid.length > 0) {
    const list = invalid.map((s) => `  - ${s}`).join('\n');
    return {
      blocked: true,
      reason:
        `BLOCKED: Direct push to 'dev' is restricted to 'docs:' and 'hotfix:' commits only.\n` +
        `Non-conforming commits:\n${list}\nPush to a feature branch and open a Pull Request into 'dev'.`,
    };
  }
  return { blocked: false };
}

export function evaluatePreCommit(options = {}) {
  const runGit = options.runGit ?? defaultRunGit;
  const currentBranch =
    options.currentBranch ?? runGit(['rev-parse', '--abbrev-ref', 'HEAD']).trim();

  if (PROTECTED_NO_DIRECT.has(currentBranch)) {
    return {
      blocked: true,
      reason: `BLOCKED: Direct commits on protected branch '${currentBranch}' are forbidden. Branch off 'dev' instead.`,
    };
  }

  return checkStagedChanges({
    runGit,
    stagedDiff: options.stagedDiff,
    stagedFiles: options.stagedFiles,
  });
}

export function evaluateBashCommand(command, options = {}) {
  if (typeof command !== 'string' || !command.trim()) {
    return { blocked: false };
  }

  const runGit = options.runGit ?? defaultRunGit;
  const currentBranch =
    options.currentBranch ?? runGit(['rev-parse', '--abbrev-ref', 'HEAD']).trim();

  const segments = splitShellSegments(command);
  let hasGitAdd = false;
  let hasGitCommit = false;

  for (const segment of segments) {
    // 1. Block .env shell reads/prints/staging
    const blockedEnv = segmentTouchesBlockedEnv(segment);
    if (blockedEnv) {
      return {
        blocked: true,
        reason:
          `BLOCKED: Shell command accesses secret file '${blockedEnv}'. ` +
          `Never read, print, or stage '.env' files via shell commands.`,
      };
    }

    const tokens = tokenizeSegment(segment);
    const gitInfo = getGitSubcommand(tokens);
    if (!gitInfo) continue;

    if (gitInfo.subcommand === 'add') {
      hasGitAdd = true;
    }

    if (gitInfo.subcommand === 'commit') {
      const hasAutoStageFlag = gitInfo.args.some((t) => {
        const v = t.value;
        return v === '-a' || v === '--all' || /^-[a-zA-Z]*a[a-zA-Z]*$/.test(v);
      });
      if (hasAutoStageFlag) {
        return {
          blocked: true,
          reason:
            `BLOCKED: Do not use 'git commit -a/--all'. Stage files explicitly with 'git add' ` +
            `and then run 'git commit' so staged diffs are scanned for secrets.`,
        };
      }
      hasGitCommit = true;
    }

    // 2. Protected branch push checks
    if (gitInfo.subcommand === 'push') {
      const { hasForce, hasAllOrMirror, targetBranches } = parseGitPushTargets(
        gitInfo.args,
        currentBranch
      );

      if (hasAllOrMirror) {
        return {
          blocked: true,
          reason: `BLOCKED: 'git push --all/--mirror' is forbidden because it can push protected branches.`,
        };
      }

      if (hasForce) {
        return {
          blocked: true,
          reason: `BLOCKED: Force pushes ('--force' / '-f') are forbidden by repository policy.`,
        };
      }

      for (const branch of targetBranches) {
        if (PROTECTED_NO_DIRECT.has(branch)) {
          return {
            blocked: true,
            reason: `BLOCKED: Direct pushes to '${branch}' are forbidden. Push to a feature branch and open a Pull Request.`,
          };
        }
        if (branch === 'dev') {
          const devCheck = checkDevPushCommits(runGit, options.pushCommitSubjects);
          if (devCheck.blocked) return devCheck;
        }
      }
    }
  }

  // 3. Block chaining `git add ... && git commit` so staged diffs are always scanned cleanly
  if (hasGitAdd && hasGitCommit) {
    return {
      blocked: true,
      reason:
        `BLOCKED: Do not chain 'git add' and 'git commit' in one command. ` +
        `Run 'git add' first, then run 'git commit' separately so staged diffs are scanned for secrets.`,
    };
  }

  // 4. On `git commit`, enforce protected branch + staged secret scan
  if (hasGitCommit) {
    return evaluatePreCommit({
      runGit,
      currentBranch,
      stagedDiff: options.stagedDiff,
      stagedFiles: options.stagedFiles,
    });
  }

  return { blocked: false };
}

function main() {
  if (process.argv.includes('--pre-commit')) {
    const result = evaluatePreCommit();
    if (result.blocked) {
      process.stderr.write(`${result.reason}\n`);
      process.exit(2);
    }
    process.exit(0);
  }

  const payload = readStdinJson();
  const toolName = payload?.tool_name ?? '';
  if (toolName && toolName !== 'Bash') {
    process.exit(0);
  }
  const command = payload?.tool_input?.command ?? payload?.command ?? '';
  const result = evaluateBashCommand(command);
  if (result.blocked) {
    process.stderr.write(`${result.reason}\n`);
    process.exit(2);
  }
  process.exit(0);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
