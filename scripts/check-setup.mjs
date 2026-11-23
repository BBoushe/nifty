#!/usr/bin/env node
// Secret-safe Git forge reader & full setup preflight checker (zero-dependency Node ESM).
//
// Security invariant (Decision 1.7 & AGENTS.md §2.8):
//   AI agents are forbidden from reading `.env` directly. This script reads ONLY
//   strictly allowlisted non-secret keys (GIT_FORGE, GITLAB_HOST, AI_EVALUATOR_PROVIDER,
//   AI_EVALUATOR_MODEL) and reports KICONNECT_API_KEY solely as a boolean status
//   ("configured" vs "placeholder"). It NEVER prints or returns any secret value.
//
// Usage:
//   node scripts/check-setup.mjs --forge       # Print active forge & CLI (gh | glab)
//   node scripts/check-setup.mjs               # Run full setup preflight check
//   node scripts/check-setup.mjs --root <dir>  # Run against a custom directory
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const ALLOWED_NON_SECRET_KEYS = new Set([
  'GIT_FORGE',
  'GITLAB_HOST',
  'AI_EVALUATOR_PROVIDER',
  'AI_EVALUATOR_MODEL',
]);

const PLACEHOLDER_KEY_RE =
  /^(|not-configured|changeme|placeholder|example|dummy|your[-_].*|<[^>]+>)$/i;

const VALID_HOSTNAME_RE = /^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?(?::\d{1,5})?$/i;

export function validateGitLabHost(rawHost) {
  const host = String(rawHost ?? '').trim();
  if (!host || !VALID_HOSTNAME_RE.test(host)) {
    throw new Error(
      `Invalid GITLAB_HOST '${host.replace(/[\r\n]/g, ' ')}'. Expected a hostname like 'gitlab.com' or 'gitlab.example.edu:8443'.`
    );
  }
  return host;
}

/**
 * Parse a .env file returning ONLY allowlisted non-secret values plus a boolean
 * indicator for whether KICONNECT_API_KEY is populated with a non-placeholder value.
 */
export function parseSafeEnvContent(content) {
  const safe = {};
  let apiKeyConfigured = false;
  if (typeof content !== 'string') return { safe, apiKeyConfigured };

  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq <= 0) continue;
    const key = line.slice(0, eq).trim();
    const rawVal = line
      .slice(eq + 1)
      .trim()
      .replace(/^["']|["']$/g, '');

    if (ALLOWED_NON_SECRET_KEYS.has(key)) {
      safe[key] = rawVal;
    } else if (key === 'KICONNECT_API_KEY') {
      apiKeyConfigured = rawVal.length > 0 && !PLACEHOLDER_KEY_RE.test(rawVal);
    }
  }

  return { safe, apiKeyConfigured };
}

/**
 * Resolve active forge config from `.env` (fallback `.env.example`, then defaults)
 * without ever returning secret values.
 */
export function resolveForgeConfig(rootDir = process.cwd(), envOverride = process.env) {
  const absRoot = path.resolve(rootDir);
  const envPath = path.join(absRoot, '.env');
  const examplePath = path.join(absRoot, '.env.example');

  const exampleParsed = fs.existsSync(examplePath)
    ? parseSafeEnvContent(fs.readFileSync(examplePath, 'utf8'))
    : { safe: {}, apiKeyConfigured: false };

  const hasDotEnv = fs.existsSync(envPath);
  const dotEnvParsed = hasDotEnv
    ? parseSafeEnvContent(fs.readFileSync(envPath, 'utf8'))
    : { safe: {}, apiKeyConfigured: false };

  const rawForge = (
    envOverride?.GIT_FORGE ||
    dotEnvParsed.safe.GIT_FORGE ||
    exampleParsed.safe.GIT_FORGE ||
    'github'
  )
    .trim()
    .toLowerCase();

  const forge = rawForge === 'gitlab' ? 'gitlab' : 'github';
  const gitlabHost = validateGitLabHost(
    envOverride?.GITLAB_HOST ||
      dotEnvParsed.safe.GITLAB_HOST ||
      exampleParsed.safe.GITLAB_HOST ||
      'gitlab.com'
  );

  const evaluatorModel =
    dotEnvParsed.safe.AI_EVALUATOR_MODEL ||
    exampleParsed.safe.AI_EVALUATOR_MODEL ||
    'OpenAI GPT OSS 120B';

  return {
    forge,
    cli: forge === 'gitlab' ? 'glab' : 'gh',
    gitlabHost,
    hasDotEnv,
    apiKeyStatus: dotEnvParsed.apiKeyConfigured ? 'configured' : 'placeholder',
    evaluatorModel,
  };
}

function defaultExec(cmd, args, cwd) {
  try {
    const out = execFileSync(cmd, args, {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim();
    return { ok: true, out };
  } catch (err) {
    return { ok: false, out: String(err?.stderr || err?.message || '').trim() };
  }
}

export function runSetupChecks(options = {}) {
  const rootDir = path.resolve(options.rootDir ?? process.cwd());
  const runCmd = options.runCmd ?? ((cmd, args) => defaultExec(cmd, args, rootDir));
  const config = resolveForgeConfig(rootDir, options.env ?? process.env);

  const checks = [];
  const add = (level, name, detail, fix = '') => checks.push({ level, name, detail, fix });

  // 1. Forge CLI check (gh or glab based on GIT_FORGE)
  if (config.forge === 'gitlab') {
    const ver = runCmd('glab', ['--version']);
    if (ver.ok) {
      add('PASS', 'GitLab CLI (glab)', `${ver.out.split('\n')[0]} (host: ${config.gitlabHost})`);
      const auth = runCmd('glab', ['auth', 'status', '--hostname', config.gitlabHost]);
      if (auth.ok) {
        add('PASS', 'GitLab Auth', `Authenticated on ${config.gitlabHost}`);
      } else {
        add(
          'WARN',
          'GitLab Auth',
          `Not authenticated on ${config.gitlabHost}`,
          `glab auth login --hostname ${config.gitlabHost}`
        );
      }
    } else {
      add(
        'FAIL',
        'GitLab CLI (glab)',
        'GIT_FORGE=gitlab is set but `glab` is not installed on PATH',
        'Install glab: https://gitlab.com/gitlab-org/cli'
      );
    }
  } else {
    const ver = runCmd('gh', ['--version']);
    if (ver.ok) {
      add('PASS', 'GitHub CLI (gh)', ver.out.split('\n')[0]);
      const auth = runCmd('gh', ['auth', 'status']);
      if (auth.ok) {
        add('PASS', 'GitHub Auth', 'Authenticated with GitHub');
      } else {
        add('WARN', 'GitHub Auth', 'Not authenticated with GitHub', 'gh auth login');
      }
    } else {
      add(
        'WARN',
        'GitHub CLI (gh)',
        '`gh` not found on PATH (needed for CLI PR creation)',
        'Install gh: https://cli.github.com/'
      );
    }
  }

  // 2. Git hooks activation
  const hooksPath = runCmd('git', ['config', '--get', 'core.hooksPath']);
  if (hooksPath.ok && hooksPath.out === '.githooks') {
    add('PASS', 'Git Hooks Path', 'core.hooksPath=.githooks');
  } else {
    add(
      'FAIL',
      'Git Hooks Path',
      `core.hooksPath is '${hooksPath.out || '(unset)'}'`,
      'git config core.hooksPath .githooks'
    );
  }

  for (const hook of ['pre-commit', 'commit-msg', 'pre-push']) {
    const hookFile = path.join(rootDir, '.githooks', hook);
    if (!fs.existsSync(hookFile)) {
      add('FAIL', `Hook .githooks/${hook}`, 'missing');
      continue;
    }
    if (process.platform !== 'win32') {
      try {
        fs.accessSync(hookFile, fs.constants.X_OK);
        add('PASS', `Hook .githooks/${hook}`, 'present & executable');
      } catch {
        add('FAIL', `Hook .githooks/${hook}`, 'not executable', `chmod +x .githooks/${hook}`);
      }
    } else {
      add('PASS', `Hook .githooks/${hook}`, 'present');
    }
  }

  // 3. .env presence & key mode (NEVER print secret values)
  if (config.hasDotEnv) {
    add(
      'PASS',
      'Local .env',
      `present (GIT_FORGE=${config.forge}, KICONNECT_API_KEY=${config.apiKeyStatus})`
    );
  } else {
    add(
      'WARN',
      'Local .env',
      'not created yet (falling back to .env.example defaults)',
      'cp .env.example .env'
    );
  }

  // 4. Harness canonical files & settings JSON
  const settingsPath = path.join(rootDir, '.claude', 'settings.json');
  try {
    JSON.parse(fs.readFileSync(settingsPath, 'utf8'));
    add('PASS', '.claude/settings.json', 'valid JSON');
  } catch {
    add('FAIL', '.claude/settings.json', 'missing or invalid JSON');
  }

  for (const rel of [
    '.agents/rules/coding-standards.md',
    'docs/decisions/index.md',
    'docs/api.md',
    '.gitlab-ci.yml',
  ]) {
    if (fs.existsSync(path.join(rootDir, rel))) {
      add('PASS', rel, 'present');
    } else {
      add('FAIL', rel, 'missing');
    }
  }

  // 5. Core runtimes
  const nodeMajor = Number.parseInt(process.versions.node.split('.')[0], 10);
  if (nodeMajor >= 22) {
    add('PASS', 'Node.js', `v${process.versions.node}`);
  } else {
    add('FAIL', 'Node.js', `v${process.versions.node} (<22 required)`, 'Install Node.js 22+ LTS');
  }

  const javaVer = runCmd('java', ['-version']);
  if (javaVer.ok) {
    add('PASS', 'Java', 'available on PATH');
  } else {
    add('WARN', 'Java', 'not detected on PATH (Java 21 required for backend)');
  }

  const dockerVer = runCmd('docker', ['--version']);
  if (dockerVer.ok) {
    add('PASS', 'Docker', dockerVer.out.split('\n')[0]);
  } else {
    add('WARN', 'Docker', 'not detected (optional for host-only mode)');
  }

  const openspecVer = runCmd('openspec', ['--version']);
  if (openspecVer.ok) {
    add('PASS', 'OpenSpec CLI', openspecVer.out.split('\n')[0]);
  } else {
    add('WARN', 'OpenSpec CLI', 'optional CLI not installed', 'npm install -g @fission-ai/openspec@latest');
  }

  const failed = checks.filter((c) => c.level === 'FAIL').length;
  const warned = checks.filter((c) => c.level === 'WARN').length;

  return { config, checks, failed, warned };
}

function parseArgs(argv) {
  const opts = { forgeOnly: false, rootDir: process.cwd() };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--forge') opts.forgeOnly = true;
    else if (argv[i] === '--root' && argv[i + 1]) {
      opts.rootDir = argv[i + 1];
      i += 1;
    }
  }
  return opts;
}

function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.forgeOnly) {
    const cfg = resolveForgeConfig(opts.rootDir);
    process.stdout.write(
      `GIT_FORGE=${cfg.forge}\nGITLAB_HOST=${cfg.gitlabHost}\nCLI=${cfg.cli}\n`
    );
    process.exit(0);
  }

  const report = runSetupChecks({ rootDir: opts.rootDir });
  process.stdout.write(
    `Nifty Setup Preflight (forge=${report.config.forge}, cli=${report.config.cli})\n`
  );
  for (const c of report.checks) {
    const fixNote = c.fix ? ` -> Fix: ${c.fix}` : '';
    process.stdout.write(`[${c.level}] ${c.name}: ${c.detail}${fixNote}\n`);
  }
  process.stdout.write(
    `Summary: ${report.checks.length - report.failed - report.warned} passed, ${report.warned} warnings, ${report.failed} failed\n`
  );
  process.exit(report.failed > 0 ? 1 : 0);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
