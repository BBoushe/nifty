#!/usr/bin/env node
/**
 * Archive gate (REQ-SL-04, Decision 3.8): reports OpenSpec changes whose tasks above
 * `## Delivery` are all checked but which still sit outside `openspec/changes/archive/`.
 */
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { getRepoRoot, readStdinJson } from './lib.mjs';

/**
 * Return the sorted names of completed, unarchived changes under `<repoRoot>/openspec/changes`.
 */
export function findCompletedUnarchivedChanges(repoRoot) {
  const changesDir = path.join(repoRoot, 'openspec', 'changes');
  let entries;
  try {
    entries = fs.readdirSync(changesDir, { withFileTypes: true });
  } catch {
    return [];
  }
  const done = [];
  for (const entry of entries) {
    if (!entry.isDirectory() || entry.name === 'archive') continue;
    let text;
    try {
      text = fs.readFileSync(path.join(changesDir, entry.name, 'tasks.md'), 'utf8');
    } catch {
      continue;
    }
    const delivery = text.search(/^## Delivery\b/m);
    const head = delivery === -1 ? text : text.slice(0, delivery);
    const checked = (head.match(/^\s*- \[x\]/gim) ?? []).length;
    const open = (head.match(/^\s*- \[ \]/gm) ?? []).length;
    if (checked > 0 && open === 0) done.push(entry.name);
  }
  return done.sort();
}

function archiveMessage(names) {
  return (
    `Archive completed OpenSpec change(s): ${names.join(', ')}. ` +
    'Run: OPENSPEC_TELEMETRY=0 openspec archive <name> -y --skip-specs (domain changes: sync specs first), ' +
    'then add the docs/feature_history.md entry.'
  );
}

function main() {
  const mode = process.argv[2];
  // NIFTY_REPO_ROOT exists only so tests can point the gate at a fixture repo.
  const repoRoot = process.env.NIFTY_REPO_ROOT || getRepoRoot();

  if (mode === '--stop-hook') {
    // A crash must never block a session: any error allows the stop.
    try {
      if (readStdinJson().stop_hook_active === true) return;
      const names = findCompletedUnarchivedChanges(repoRoot);
      if (names.length > 0) {
        process.stdout.write(JSON.stringify({ decision: 'block', reason: archiveMessage(names) }));
      }
    } catch {
      // allow the stop
    }
    return;
  }

  if (mode === '--pre-push') {
    if (process.env.NIFTY_SKIP_GATE === '1') return;
    const names = findCompletedUnarchivedChanges(repoRoot);
    if (names.length > 0) {
      process.stderr.write(`archive-gate: ${archiveMessage(names)}\n`);
      process.exitCode = 1;
    }
    return;
  }

  process.stderr.write('Usage: archive-gate.mjs --stop-hook | --pre-push\n');
  process.exitCode = 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
