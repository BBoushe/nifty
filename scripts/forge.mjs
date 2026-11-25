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
//   node scripts/forge.mjs prs                                 # open PRs and MRs side by side by branch, with CI state (Decision 3.11)
import { execFileSync, spawnSync } from 'node:child_process';
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

const FAILED_STATES = new Set(['FAILURE', 'ERROR', 'CANCELLED', 'TIMED_OUT', 'ACTION_REQUIRED', 'STARTUP_FAILURE']);
const PASSED_STATES = new Set(['SUCCESS', 'NEUTRAL', 'SKIPPED']);

/** One-line state of a PR's checks: failed wins over pending, pending over pass (REQ-OR-02). */
export function checksSummary(states) {
  if (!states.length) return 'no checks';
  const failed = states.filter((s) => FAILED_STATES.has(s)).length;
  if (failed) return `${failed} failed`;
  const pending = states.filter((s) => !PASSED_STATES.has(s)).length;
  if (pending) return `${pending} pending`;
  return `${states.length}/${states.length} pass`;
}

/** Join open GitHub PRs and GitLab MRs by branch, so nobody has to match `#n` to `!m`. */
export function prRows(github, gitlab) {
  const rows = new Map();
  const row = (branch) => rows.get(branch) ?? rows.set(branch, { branch, pr: '—', checks: '—', mr: '—', pipeline: '—' }).get(branch);
  for (const p of github) Object.assign(row(p.branch), { pr: `#${p.number}`, checks: p.checks });
  for (const m of gitlab) Object.assign(row(m.branch), { mr: `!${m.iid}`, pipeline: m.pipeline });
  return [...rows.values()].sort((a, b) => a.branch.localeCompare(b.branch));
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

function runJson(cmd, args) {
  return JSON.parse(execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }));
}

function prs(forge) {
  const urls = remoteUrls(forge);
  const github = [];
  const gitlab = [];
  if (urls.github) {
    const repo = urls.github.replace(/^https:\/\//, '');
    for (const p of runJson('gh', ['pr', 'list', '--repo', repo, '--state', 'open', '--json', 'number,headRefName,statusCheckRollup'])) {
      const states = (p.statusCheckRollup ?? []).map((c) => c.conclusion || c.state || 'PENDING');
      github.push({ branch: p.headRefName, number: p.number, checks: checksSummary(states) });
    }
  }
  if (urls.gitlab) {
    // `-R <url>` lets glab resolve the project; instances under a sub-path (`/01/…`) break hand-built API paths.
    for (const m of runJson('glab', ['mr', 'list', '-R', urls.gitlab, '-F', 'json'])) {
      const full = runJson('glab', ['mr', 'view', String(m.iid), '-R', urls.gitlab, '-F', 'json']);
      gitlab.push({ branch: m.source_branch, iid: m.iid, pipeline: full.head_pipeline?.status ?? 'none' });
    }
  }
  const rows = prRows(github, gitlab);
  if (!rows.length) {
    process.stdout.write('No open PRs or MRs.\n');
    return 0;
  }
  const cols = ['branch', 'pr', 'checks', 'mr', 'pipeline'];
  const width = Object.fromEntries(cols.map((c) => [c, Math.max(c.length, ...rows.map((r) => String(r[c]).length))]));
  const line = (r) => cols.map((c) => String(r[c]).padEnd(width[c])).join('  ').trimEnd();
  process.stdout.write(`${line({ branch: 'branch', pr: 'GitHub', checks: 'checks', mr: 'GitLab', pipeline: 'pipeline' })}\n`);
  for (const r of rows) process.stdout.write(`${line(r)}\n`);
  return 0;
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
  if (command === 'prs') return prs(forge);

  process.stderr.write('Usage: forge.mjs push | pr --title <t> --body-file <f> [--head <b>] | sync [branch...] | status | prs  [--dry-run]\n');
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
