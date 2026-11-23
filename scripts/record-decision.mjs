#!/usr/bin/env node
// Deterministic hierarchical decision recorder (zero-dependency Node ESM).
// Computes the next <category>.<n> ID, appends a structured entry to
// docs/decisions/<category>-*.md, updates docs/decisions/index.md, and
// optionally marks an Open Team Decision (O1-O7) as resolved.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export const CATEGORIES = {
  '1': { file: '1-architecture.md', label: 'Architecture' },
  '2': { file: '2-design.md', label: 'Design' },
  '3': { file: '3-team-process.md', label: 'Team & Process' },
  '4': { file: '4-direction.md', label: 'Direction' },
};

export const VALID_TRIGGERS = new Set([
  'question-round',
  'prompter-change',
  'team',
  'baseline',
]);

export function slugifyHeading(text) {
  return text
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9_\s-]/g, '')
    .replace(/\s/g, '-');
}

export function computeNextDecisionId(categoryContent, category) {
  const re = new RegExp(`^##\\s+${category}\\.(\\d+)\\b`, 'gm');
  let maxNum = 0;
  let match;
  while ((match = re.exec(categoryContent)) !== null) {
    const num = Number.parseInt(match[1], 10);
    if (Number.isFinite(num) && num > maxNum) {
      maxNum = num;
    }
  }
  return `${category}.${maxNum + 1}`;
}

export function formatDecisionEntry({
  id,
  topic,
  date,
  trigger,
  context,
  decision,
  alternatives,
  rationale,
  pointers,
  resolves,
}) {
  const lines = [
    `## ${id} — ${topic}`,
    ...(resolves ? [`- **Resolves:** ${resolves}`] : []),
    `- **Date:** ${date}`,
    `- **Triggered By:** ${trigger}`,
    `- **Context:** ${context}`,
    `- **Decision:** ${decision}`,
    `- **Alternatives Considered:** ${alternatives || 'None explicitly recorded'}`,
    `- **Rationale / Justification:** ${rationale}`,
    `- **Affected Files / Pointers:** ${pointers || 'TBD'}`,
    '',
  ];
  return lines.join('\n');
}

export function formatIndexRow({ id, categoryLabel, topic, date, trigger, file }) {
  const anchor = slugifyHeading(`${id} — ${topic}`);
  const safeTopic = topic.replaceAll('|', '\\|');
  return `| **${id}** | — | ${categoryLabel} | ${safeTopic} | Decided | ${date} | ${trigger} | [${file}](./${file}#${anchor}) |`;
}

export function markOpenDecisionResolved(indexContent, openId, decisionId) {
  if (!openId) return indexContent;
  const escaped = openId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const rowRe = new RegExp(`^(\\|\\s*\\*\\*${escaped}\\*\\*\\s*\\|[^\\r\\n]*)\\|\\s*Open\\s*\\|(\\r?)$`, 'm');
  if (!rowRe.test(indexContent)) {
    return indexContent;
  }
  return indexContent.replace(rowRe, (_, prefix, cr) => `${prefix}| Resolved -> **${decisionId}** |${cr}`);
}

export function recordDecision(options) {
  const category = String(options.category ?? '').trim();
  const catMeta = Object.hasOwn(CATEGORIES, category) ? CATEGORIES[category] : null;
  if (!catMeta) {
    throw new Error(`Invalid --category '${category}'. Expected one of: 1, 2, 3, 4.`);
  }

  const topic = String(options.topic ?? '').trim();
  const trigger = String(options.trigger ?? 'team').trim();
  const context = String(options.context ?? '').trim();
  const decision = String(options.decision ?? '').trim();
  const rationale = String(options.rationale ?? '').trim();
  const alternatives = String(options.alternatives ?? '').trim();
  const pointers = String(options.pointers ?? '').trim();
  const resolves = String(options.resolves ?? '').trim();
  const date = String(options.date ?? new Date().toISOString().slice(0, 10)).trim();
  const dryRun = Boolean(options.dryRun);
  const rootDir = path.resolve(options.root ?? process.cwd());

  if (!topic) throw new Error('Missing required --topic.');
  if (!context) throw new Error('Missing required --context.');
  if (!decision) throw new Error('Missing required --decision.');
  if (!rationale) throw new Error('Missing required --rationale.');
  if (!VALID_TRIGGERS.has(trigger)) {
    throw new Error(
      `Invalid --trigger '${trigger}'. Expected one of: ${[...VALID_TRIGGERS].join(', ')}.`
    );
  }

  const decisionsDir = path.join(rootDir, 'docs', 'decisions');
  const categoryPath = path.join(decisionsDir, catMeta.file);
  const indexPath = path.join(decisionsDir, 'index.md');

  if (!fs.existsSync(categoryPath)) {
    throw new Error(`Category file not found: ${categoryPath}`);
  }
  if (!fs.existsSync(indexPath)) {
    throw new Error(`Decision index not found: ${indexPath}`);
  }

  const categoryContent = fs.readFileSync(categoryPath, 'utf8');
  const indexContent = fs.readFileSync(indexPath, 'utf8');

  const id = computeNextDecisionId(categoryContent, category);
  const entry = formatDecisionEntry({
    id,
    topic,
    date,
    trigger,
    context,
    decision,
    alternatives,
    rationale,
    pointers,
    resolves,
  });

  const indexRow = formatIndexRow({
    id,
    categoryLabel: catMeta.label,
    topic,
    date,
    trigger,
    file: catMeta.file,
  });

  const marker = '<!-- DECISION_INDEX_END -->';
  if (!indexContent.includes(marker)) {
    throw new Error(`Marker '${marker}' not found in ${indexPath}`);
  }

  const updatedCategory = `${categoryContent.trimEnd()}\n\n${entry}`;
  let updatedIndex = indexContent.replace(marker, () => `${indexRow}\n${marker}`);
  if (resolves) {
    updatedIndex = markOpenDecisionResolved(updatedIndex, resolves, id);
  }

  if (!dryRun) {
    fs.writeFileSync(categoryPath, updatedCategory, 'utf8');
    fs.writeFileSync(indexPath, updatedIndex, 'utf8');
  }

  return {
    id,
    categoryFile: path.join('docs', 'decisions', catMeta.file),
    entry,
    indexRow,
    dryRun,
  };
}

function parseCliArgs(argv) {
  const opts = {};
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--dry-run') {
      opts.dryRun = true;
      continue;
    }
    if (arg === '--help' || arg === '-h') {
      opts.help = true;
      continue;
    }
    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      const next = argv[i + 1];
      if (next !== undefined && !next.startsWith('--')) {
        opts[key] = next;
        i += 1;
      }
    }
  }
  return opts;
}

function printHelp() {
  process.stdout.write(
    [
      'Usage: node scripts/record-decision.mjs \\',
      '  --category 1|2|3|4 \\',
      '  --topic "Short title" \\',
      '  --trigger "question-round|prompter-change|team" \\',
      '  --context "Why this came up" \\',
      '  --decision "What we decided" \\',
      '  --alternatives "Other options considered" \\',
      '  --rationale "Why this option won" \\',
      '  --pointers "path/a, path/b" \\',
      '  [--resolves O1] [--date YYYY-MM-DD] [--dry-run] [--root <dir>]',
      '',
    ].join('\n')
  );
}

function main() {
  const opts = parseCliArgs(process.argv.slice(2));
  if (opts.help) {
    printHelp();
    process.exit(0);
  }
  try {
    const res = recordDecision(opts);
    const prefix = res.dryRun ? '[DRY RUN] Would record' : 'Recorded';
    process.stdout.write(`${prefix} Decision ${res.id} in ${res.categoryFile}\n`);
    process.exit(0);
  } catch (err) {
    process.stderr.write(`ERROR: ${err.message}\n`);
    process.exit(1);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
