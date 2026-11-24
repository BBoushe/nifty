#!/usr/bin/env node
/**
 * Archive gate (REQ-SL-04, Decision 3.9): reports OpenSpec changes whose tasks above
 * `## Delivery` are all checked but which still sit outside `openspec/changes/archive/`.
 */
import fs from 'node:fs';
import path from 'node:path';

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
