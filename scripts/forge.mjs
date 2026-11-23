#!/usr/bin/env node
// Dual-forge helper (Decision 1.8, zero-dependency Node ESM).
// Runs push / PR+MR / mirror-sync against GitHub (remote `origin`, primary) and GitLab
// (remote `gitlab`, secondary) according to GIT_FORGE=github|gitlab|both from `.env`.
// Reads the forge via check-setup.mjs, so `.env` secrets are never touched.
//
//   node scripts/forge.mjs push [--dry-run]                    # push current branch
//   node scripts/forge.mjs pr --title "<t>" --body-file <f> [--base dev] [--head <b>] [--dry-run]
//   node scripts/forge.mjs sync [branch...] [--dry-run]        # fast-forward gitlab to origin (default: dev alpha master)
//   node scripts/forge.mjs status                              # GitHub vs GitLab drift (exit 1 if GitLab is not behind-only)
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

import { resolveForgeConfig } from './check-setup.mjs';

const REMOTE = { github: 'origin', gitlab: 'gitlab' };
const MIRRORED = ['dev', 'alpha', 'master'];

export function targetsFor(forge) {
  return forge === 'both' ? ['github', 'gitlab'] : [forge];
}

/** Normalise an https or scp-style ssh remote URL to `https://host/path` (drops credentials and `.git`). */
export function repoUrl(remoteUrl) {
  const m = /^(?:https?:\/\/(?:[^@/]+@)?([^/]+)\/|[^@/]+@([^:]+):)(.+?)(?:\.git)?\/?$/.exec(
    String(remoteUrl).trim()
  );
  if (!m) throw new Error(`Unsupported remote URL: ${remoteUrl}`);
  return `https://${m[1] ?? m[2]}/${m[3]}`;
}

// GitHub is primary: it is pushed first and carries the upstream (-u).
export function planPush(forge, branch) {
  return targetsFor(forge).map((t, i) => ({
    forge: t,
    cmd: 'git',
    args: ['push', ...(i === 0 ? ['-u'] : []), REMOTE[t], branch],
  }));
}

export function planPr(forge, { base, head, title, body, urls }) {
  return targetsFor(forge).map((t) =>
    t === 'github'
      ? {
          forge: t,
          cmd: 'gh',
          // Explicit --repo: with two remotes gh/glab must not guess which host to use.
          args: ['pr', 'create', '--repo', urls.github.replace(/^https:\/\//, ''), '--base', base, '--head', head, '--title', title, '--body', body],
        }
      : {
          forge: t,
          cmd: 'glab',
          args: ['mr', 'create', '-R', urls.gitlab, '--target-branch', base, '--source-branch', head, '--title', title, '--description', body, '--yes'],
        }
  );
}

// Mirror = push GitHub's already-merged tip to GitLab. `--no-verify` skips only our direct-push
// hook (these commits already passed GitHub's protected-branch review); there is no --force,
// so a diverged GitLab branch is rejected instead of overwritten.
export function planSync(forge, branches = MIRRORED) {
  if (forge !== 'both') return [];
  return [
    { forge: 'github', cmd: 'git', args: ['fetch', 'origin'] },
    ...branches.map((b) => ({
      forge: 'gitlab',
      cmd: 'git',
      args: ['push', '--no-verify', REMOTE.gitlab, `refs/remotes/origin/${b}:refs/heads/${b}`],
    })),
  ];
}

function git(args) {
  const r = spawnSync('git', args, { encoding: 'utf8' });
  return { ok: r.status === 0, out: (r.stdout || '').trim() };
}

function show(step) {
  const shown = step.args.map((a) => (a.length > 60 ? `<${a.length} chars>` : a));
  process.stdout.write(`[${step.forge}] $ ${step.cmd} ${shown.join(' ')}\n`);
}

/** Runs steps in order. `stopOnFail` keeps GitLab from getting ahead of GitHub on push. */
function runSteps(steps, { dryRun, stopOnFail }) {
  const failed = [];
  for (const step of steps) {
    show(step);
    if (dryRun) continue;
    const r = spawnSync(step.cmd, step.args, { stdio: 'inherit' });
    if (r.status !== 0) {
      failed.push(step);
      process.stderr.write(`FAILED [${step.forge}] (exit ${r.status ?? 'spawn error'})\n`);
      if (stopOnFail) break;
    }
  }
  return failed;
}

function remoteUrls(forge) {
  const urls = {};
  for (const t of targetsFor(forge)) {
    const r = git(['remote', 'get-url', REMOTE[t]]);
    if (!r.ok) throw new Error(`Git remote '${REMOTE[t]}' is missing. Run: git remote add ${REMOTE[t]} <${t}-repo-url>`);
    urls[t] = repoUrl(r.out);
  }
  return urls;
}

function parseFlags(argv) {
  const f = { positional: [], dryRun: false };
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (a === '--dry-run') f.dryRun = true;
    else if (['--title', '--body-file', '--base', '--head'].includes(a)) {
      f[a.slice(2)] = argv[(i += 1)];
    } else f.positional.push(a);
  }
  return f;
}

function currentBranch() {
  const b = git(['rev-parse', '--abbrev-ref', 'HEAD']);
  if (!b.ok || b.out === 'HEAD') throw new Error('Detached HEAD: check out a branch first.');
  return b.out;
}

function status(forge) {
  if (forge !== 'both') {
    process.stdout.write(`GIT_FORGE=${forge}: nothing to compare.\n`);
    return 0;
  }
  runSteps([{ forge: 'both', cmd: 'git', args: ['fetch', '--quiet', 'origin'] }, { forge: 'both', cmd: 'git', args: ['fetch', '--quiet', 'gitlab'] }], { stopOnFail: true });
  let diverged = false;
  for (const b of new Set([...MIRRORED, currentBranch()])) {
    const r = git(['rev-list', '--left-right', '--count', `origin/${b}...gitlab/${b}`]);
    if (!r.ok) {
      process.stdout.write(`${b}: missing on one side (not pushed to both yet)\n`);
      continue;
    }
    const [gh, gl] = r.out.split(/\s+/).map(Number);
    if (gl > 0) diverged = true;
    process.stdout.write(`${b}: GitHub ahead ${gh}, GitLab ahead ${gl}${gl > 0 ? '  <-- GitLab has commits GitHub lacks' : gh > 0 ? '  (run: node scripts/forge.mjs sync)' : ''}\n`);
  }
  return diverged ? 1 : 0;
}

function main(argv) {
  const [command, ...rest] = argv;
  const flags = parseFlags(rest);
  const { forge } = resolveForgeConfig();

  if (command === 'push') {
    const failed = runSteps(planPush(forge, currentBranch()), { dryRun: flags.dryRun, stopOnFail: true });
    if (failed[0]?.forge === 'gitlab') {
      process.stderr.write('GitHub already has this branch but GitLab does not. Fix the cause, then re-run `node scripts/forge.mjs push`.\n');
    }
    return failed.length ? 1 : 0;
  }

  if (command === 'pr') {
    if (!flags.title || !flags['body-file']) throw new Error('pr needs --title and --body-file');
    const steps = planPr(forge, {
      base: flags.base ?? 'dev',
      head: flags.head ?? currentBranch(),
      title: flags.title,
      body: fs.readFileSync(flags['body-file'], 'utf8'),
      urls: remoteUrls(forge),
    });
    const failed = runSteps(steps, { dryRun: flags.dryRun, stopOnFail: false });
    if (forge === 'both' && !flags.dryRun) {
      process.stdout.write('Merge on GitHub (primary), then run `node scripts/forge.mjs sync` so the GitLab MR closes as merged.\n');
    }
    return failed.length ? 1 : 0;
  }

  if (command === 'sync') {
    const steps = planSync(forge, flags.positional.length ? flags.positional : MIRRORED);
    if (!steps.length) {
      process.stdout.write(`GIT_FORGE=${forge}: sync only applies to GIT_FORGE=both.\n`);
      return 0;
    }
    return runSteps(steps, { dryRun: flags.dryRun, stopOnFail: false }).length ? 1 : 0;
  }

  if (command === 'status') return status(forge);

  process.stderr.write('Usage: forge.mjs push | pr --title <t> --body-file <f> | sync [branch...] | status  [--dry-run]\n');
  return 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    process.exit(main(process.argv.slice(2)));
  } catch (err) {
    process.stderr.write(`ERROR: ${err.message}\n`);
    process.exit(1);
  }
}
