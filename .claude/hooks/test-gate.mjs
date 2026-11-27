#!/usr/bin/env node
/**
 * Fast path-scoped build gate (cross-platform Node ESM, zero dependencies).
 *
 * Inspects touched files and runs only the relevant fast compile/build checks:
 *  - frontend/ changed -> `npm run typecheck` + `npm run build` (`CI=true`)
 *  - backend/ changed  -> `./mvnw -q test-compile` (Windows: `mvnw.cmd -q test-compile`)
 *
 * Never runs slow PostgreSQL Testcontainers. Honors `NIFTY_SKIP_GATE=1`.
 */
import { execFileSync, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { getRepoRoot, normalizePath } from './lib.mjs';

export function planGateCommands(touchedFiles, platform = process.platform) {
  const normalized = (touchedFiles ?? []).map((f) => normalizePath(f)).filter(Boolean);
  const frontendChanged = normalized.some((f) => f.startsWith('frontend/'));
  const backendChanged = normalized.some((f) => f.startsWith('backend/'));

  const isWin = platform === 'win32';
  const plan = [];

  if (frontendChanged) {
    const npmBin = isWin ? 'npm.cmd' : 'npm';
    plan.push({
      label: 'frontend typecheck',
      cwd: 'frontend',
      cmd: npmBin,
      args: ['run', 'typecheck'],
      env: { CI: 'true' },
    });
    plan.push({
      label: 'frontend build',
      cwd: 'frontend',
      cmd: npmBin,
      args: ['run', 'build'],
      env: { CI: 'true' },
    });
  }

  if (backendChanged) {
    plan.push({
      label: 'backend test-compile',
      cwd: 'backend',
      cmd: isWin ? 'mvnw.cmd' : './mvnw',
      args: ['-q', 'test-compile'],
      env: {},
    });
  }

  return plan;
}

function detectTouchedFiles(repoRoot) {
  const files = new Set();
  const runGit = (args) => {
    try {
      return execFileSync('git', args, {
        cwd: repoRoot,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
      });
    } catch {
      return '';
    }
  };

  // Compare against upstream, origin/dev, or HEAD~1
  let baseRange = '';
  if (runGit(['rev-parse', '--verify', '--quiet', '@{upstream}']).trim()) {
    baseRange = '@{upstream}..HEAD';
  } else if (runGit(['rev-parse', '--verify', '--quiet', 'origin/dev']).trim()) {
    baseRange = 'origin/dev...HEAD';
  } else if (runGit(['rev-parse', '--verify', '--quiet', 'HEAD~1']).trim()) {
    baseRange = 'HEAD~1..HEAD';
  }

  const sources = [
    baseRange ? runGit(['diff', '--name-only', baseRange]) : '',
    runGit(['diff', '--cached', '--name-only']),
    runGit(['diff', '--name-only']),
  ];

  for (const out of sources) {
    for (const line of out.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (trimmed) files.add(trimmed);
    }
  }

  return [...files];
}

function parseFilesArg(argv) {
  const idx = argv.indexOf('--files');
  if (idx !== -1 && argv[idx + 1]) {
    return argv[idx + 1].split(',').map((s) => s.trim()).filter(Boolean);
  }
  return null;
}

function main() {
  if (process.env.NIFTY_SKIP_GATE === '1') {
    process.stderr.write('test-gate: skipped (NIFTY_SKIP_GATE=1).\n');
    process.exit(0);
  }

  const repoRoot = getRepoRoot();
  const explicitFiles = parseFilesArg(process.argv.slice(2));
  const touchedFiles = explicitFiles ?? detectTouchedFiles(repoRoot);
  const plan = planGateCommands(touchedFiles);

  if (plan.length === 0) {
    process.stderr.write('test-gate: no backend/ or frontend/ changes; skipping compile gate.\n');
    process.exit(0);
  }

  for (const step of plan) {
    const stepCwd = path.join(repoRoot, step.cwd);
    if (step.cwd === 'frontend' && !fs.existsSync(path.join(stepCwd, 'node_modules'))) {
      process.stderr.write(
        `test-gate: frontend/node_modules missing. Run 'cd frontend && npm ci' (or set NIFTY_SKIP_GATE=1).\n`
      );
      process.exit(1);
    }
    process.stderr.write(`test-gate: running ${step.label}...\n`);
    const res = spawnSync(step.cmd, step.args, {
      cwd: stepCwd,
      stdio: 'inherit',
      env: { ...process.env, ...step.env },
      shell: process.platform === 'win32',
    });
    if (res.status !== 0) {
      process.stderr.write(`test-gate: FAILED on ${step.label} (exit ${res.status ?? 1}).\n`);
      process.exit(res.status ?? 1);
    }
  }

  process.stderr.write('test-gate: all fast compile checks passed.\n');
  process.exit(0);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
