import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { describe, it } from 'node:test';

import {
  parseSafeEnvContent,
  resolveForgeConfig,
  runSetupChecks,
  validateGitLabHost,
} from './check-setup.mjs';
import { planPr, planPush, planSync, repoUrl, targetsFor } from './forge.mjs';
import {
  computeNextDecisionId,
  recordDecision,
  slugifyHeading,
} from './record-decision.mjs';

const FAKE_SECRET_KEY = ['fhai', 'secret', 'token', '987654321'].join('_');
const FAKE_DB_PASS = ['super', 'secret', 'dbpass', '2026'].join('_');

describe('scripts/record-decision.mjs', () => {
  it('computes next sequential hierarchical X.Y ID cleanly and matches GitHub heading anchors', () => {
    assert.equal(computeNextDecisionId('# Category 4\n\n---\n', '4'), '4.1');
    assert.equal(
      computeNextDecisionId('## 1.1 — First\n\n## 1.2 — Second\n', '1'),
      '1.3'
    );
    assert.equal(slugifyHeading('2.2 — Select PrimeVue_v4'), '22--select-primevue_v4');
    assert.throws(
      () =>
        recordDecision({
          category: 'toString',
          topic: 'Prototype check',
          trigger: 'team',
          context: 'c',
          decision: 'd',
          rationale: 'r',
        }),
      /Invalid --category/
    );
  });

  it('appends decision entry, updates index.md (including CRLF & $& safety), and resolves open decision in isolated temp dir', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'nifty-decisions-'));
    try {
      const decDir = path.join(tmp, 'docs', 'decisions');
      fs.mkdirSync(decDir, { recursive: true });
      fs.writeFileSync(
        path.join(decDir, '2-design.md'),
        '# Category 2\n\n## 2.1 — Baseline\n- **Decision:** Baseline\n',
        'utf8'
      );
      fs.writeFileSync(
        path.join(decDir, 'index.md'),
        [
          '| ID | Legacy ID | Category | Topic | Status | Date | Trigger | Details |',
          '|---|---|---|---|---|---|---|---|',
          '| **2.1** | D1 | Design | Baseline | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#21--baseline) |',
          '<!-- DECISION_INDEX_END -->',
          '',
          '| ID | Question | Options | Owner | Status |',
          '|---|---|---|---|---|',
          '| **O1** | UI kit | PrimeVue vs shadcn-vue | Frontend | Open |',
          '',
        ].join('\r\n'),
        'utf8'
      );

      const res = recordDecision({
        root: tmp,
        category: '2',
        topic: 'Select PrimeVue ($& safe)',
        trigger: 'question-round',
        context: 'Team needs fast data tables and sliders.',
        decision: 'Use PrimeVue v4 with Tailwind v4.',
        alternatives: 'shadcn-vue',
        rationale: 'Saves 1-2 days during block week.',
        pointers: 'frontend/package.json',
        resolves: 'O1',
        date: '2026-11-23',
      });

      assert.equal(res.id, '2.2');
      const catText = fs.readFileSync(path.join(decDir, '2-design.md'), 'utf8');
      const idxText = fs.readFileSync(path.join(decDir, 'index.md'), 'utf8');

      assert.equal(catText.includes('## 2.2 — Select PrimeVue ($& safe)'), true);
      assert.equal(catText.includes('- **Triggered By:** question-round'), true);
      assert.equal(catText.includes('- **Resolves:** O1'), true);
      assert.equal(idxText.includes('| **2.2** | — | Design | Select PrimeVue ($& safe) |'), true);
      assert.equal(idxText.includes('<!-- DECISION_INDEX_END -->'), true);
      assert.equal(idxText.includes('./2-design.md#22--select-primevue--safe)'), true);
      assert.equal(idxText.includes('| Resolved -> **2.2** |'), true);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

describe('scripts/check-setup.mjs', () => {
  it('reads only allowlisted non-secret keys and NEVER leaks secrets', () => {
    const envText = [
      `KICONNECT_API_KEY=${FAKE_SECRET_KEY}`,
      `POSTGRES_PASSWORD=${FAKE_DB_PASS}`,
      'GIT_FORGE=gitlab',
      'GITLAB_HOST=gitlab.fhdortmund.de',
      'AI_EVALUATOR_MODEL=Qwen 3.8 27B',
    ].join('\n');

    const parsed = parseSafeEnvContent(envText);
    assert.equal(parsed.safe.GIT_FORGE, 'gitlab');
    assert.equal(parsed.safe.GITLAB_HOST, 'gitlab.fhdortmund.de');
    assert.equal(parsed.apiKeyConfigured, true);

    const serialized = JSON.stringify(parsed);
    assert.equal(serialized.includes(FAKE_SECRET_KEY), false);
    assert.equal(serialized.includes(FAKE_DB_PASS), false);
  });

  it('switches cleanly between github (gh) and gitlab (glab) via .env', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'nifty-forge-'));
    try {
      fs.writeFileSync(
        path.join(tmp, '.env.example'),
        'GIT_FORGE=github\nGITLAB_HOST=gitlab.com\n',
        'utf8'
      );
      const defaultCfg = resolveForgeConfig(tmp, {});
      assert.equal(defaultCfg.forge, 'github');
      assert.equal(defaultCfg.cli, 'gh');

      fs.writeFileSync(
        path.join(tmp, '.env'),
        `GIT_FORGE=gitlab\nGITLAB_HOST=gitlab.uni.example\nKICONNECT_API_KEY=${FAKE_SECRET_KEY}\n`,
        'utf8'
      );
      const glCfg = resolveForgeConfig(tmp, {});
      assert.equal(glCfg.forge, 'gitlab');
      assert.equal(glCfg.cli, 'glab');
      assert.equal(glCfg.gitlabHost, 'gitlab.uni.example');
      assert.equal(glCfg.apiKeyStatus, 'configured');
      assert.equal(JSON.stringify(glCfg).includes(FAKE_SECRET_KEY), false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it('rejects malformed or injection-attempt GITLAB_HOST values', () => {
    assert.equal(validateGitLabHost('gitlab.fhdortmund.de:8443'), 'gitlab.fhdortmund.de:8443');
    assert.throws(() => validateGitLabHost('--help'), /Invalid GITLAB_HOST/);
    assert.throws(() => validateGitLabHost('gitlab.com\nCLI=evil'), /Invalid GITLAB_HOST/);
    assert.throws(() => validateGitLabHost('bad host'), /Invalid GITLAB_HOST/);
  });

  it('runs preflight checks for gitlab mode without exposing secrets', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'nifty-preflight-'));
    try {
      fs.mkdirSync(path.join(tmp, '.githooks'), { recursive: true });
      fs.mkdirSync(path.join(tmp, '.claude'), { recursive: true });
      fs.mkdirSync(path.join(tmp, '.agents', 'rules'), { recursive: true });
      fs.mkdirSync(path.join(tmp, 'docs', 'decisions'), { recursive: true });

      for (const hook of ['pre-commit', 'commit-msg', 'pre-push']) {
        const p = path.join(tmp, '.githooks', hook);
        fs.writeFileSync(p, '#!/bin/sh\n');
        fs.chmodSync(p, 0o755);
      }
      fs.writeFileSync(path.join(tmp, '.claude', 'settings.json'), '{}');
      fs.writeFileSync(path.join(tmp, '.agents', 'rules', 'coding-standards.md'), '# Rules\n');
      fs.writeFileSync(path.join(tmp, 'docs', 'decisions', 'index.md'), '# Index\n');
      fs.writeFileSync(path.join(tmp, 'docs', 'api.md'), '# API\n');
      fs.writeFileSync(path.join(tmp, '.gitlab-ci.yml'), 'stages: [test]\n');
      fs.writeFileSync(
        path.join(tmp, '.env'),
        `GIT_FORGE=gitlab\nGITLAB_HOST=gitlab.uni.example\nKICONNECT_API_KEY=${FAKE_SECRET_KEY}\n`
      );

      const report = runSetupChecks({
        rootDir: tmp,
        env: {},
        runCmd: (cmd, args) => {
          if (cmd === 'glab' && args[0] === '--version') return { ok: true, out: 'glab version 1.50.0' };
          if (cmd === 'glab' && args[0] === 'auth') return { ok: true, out: 'Logged in' };
          if (cmd === 'git' && args[0] === 'config') return { ok: true, out: '.githooks' };
          return { ok: true, out: 'ok' };
        },
      });

      assert.equal(report.failed, 0);
      assert.equal(report.config.cli, 'glab');
      assert.equal(JSON.stringify(report).includes(FAKE_SECRET_KEY), false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

describe('GIT_FORGE=both (Decision 1.8)', () => {
  it('resolves both forges and CLIs; unknown values still fall back to github', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'nifty-both-'));
    try {
      fs.writeFileSync(path.join(tmp, '.env'), 'GIT_FORGE=both\n', 'utf8');
      const cfg = resolveForgeConfig(tmp, {});
      assert.equal(cfg.forge, 'both');
      assert.equal(cfg.cli, 'gh,glab');
      assert.equal(resolveForgeConfig(tmp, { GIT_FORGE: 'bitbucket' }).forge, 'github');
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it('preflight checks both CLIs and both remotes', () => {
    const seen = [];
    const report = runSetupChecks({
      rootDir: os.tmpdir(),
      env: { GIT_FORGE: 'both' },
      runCmd: (cmd, args) => {
        seen.push([cmd, args[0]].join(' '));
        return { ok: true, out: 'ok' };
      },
    });
    assert.ok(seen.includes('gh --version') && seen.includes('glab --version'));
    const names = report.checks.map((c) => c.name);
    assert.ok(names.includes("Git remote 'origin'") && names.includes("Git remote 'gitlab'"));
  });
});

describe('scripts/forge.mjs', () => {
  const urls = { github: 'https://github.com/o/r', gitlab: 'https://git.example.edu/g/sub/r' };

  it('targets GitHub first, GitLab second, only the configured forge otherwise', () => {
    assert.deepEqual(targetsFor('both'), ['github', 'gitlab']);
    assert.deepEqual(targetsFor('gitlab'), ['gitlab']);
    assert.deepEqual(
      planPush('both', 'feat/x').map((s) => s.args.join(' ')),
      ['push -u origin feat/x', 'push gitlab feat/x']
    );
    assert.equal(planPush('gitlab', 'feat/x')[0].args.join(' '), 'push -u gitlab feat/x');
  });

  it('builds a PR and an MR with explicit repos so gh/glab never guess the host', () => {
    const [pr, mr] = planPr('both', { base: 'dev', head: 'feat/x', title: 't', body: 'b', urls });
    assert.deepEqual(pr.args.slice(0, 4), ['pr', 'create', '--repo', 'github.com/o/r']);
    assert.deepEqual(mr.args.slice(0, 4), ['mr', 'create', '-R', urls.gitlab]);
    assert.ok(mr.args.includes('--yes'));
    assert.equal(planPr('github', { base: 'dev', head: 'h', title: 't', body: 'b', urls }).length, 1);
  });

  it('mirrors origin to gitlab without force, and only in both mode', () => {
    assert.deepEqual(planSync('github'), []);
    const steps = planSync('both', ['dev']);
    assert.deepEqual(steps[0].args, ['fetch', 'origin']);
    assert.deepEqual(steps[1].args, ['push', '--no-verify', 'gitlab', 'refs/remotes/origin/dev:refs/heads/dev']);
    assert.ok(steps.every((s) => !s.args.some((a) => a.includes('force') || a === '-f')));
  });

  it('normalises https and ssh remotes and drops embedded credentials', () => {
    assert.equal(repoUrl('https://github.com/o/r.git'), 'https://github.com/o/r');
    assert.equal(repoUrl('https://user:tok@git.example.edu/g/sub/r.git'), 'https://git.example.edu/g/sub/r');
    assert.equal(repoUrl('git@github.com:o/r.git'), 'https://github.com/o/r');
    assert.throws(() => repoUrl('not a url'), /Unsupported/);
  });
});

describe('feature history', () => {
  const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');

  function missingHistoryEntries() {
    const archiveDir = path.join(root, 'openspec/changes/archive');
    const historyPath = path.join(root, 'docs/feature_history.md');
    const history = fs.existsSync(historyPath) ? fs.readFileSync(historyPath, 'utf8') : '';
    const missing = [];
    for (const entry of fs.readdirSync(archiveDir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const match = /^(\d{4}-\d{2}-\d{2})-(.+)$/.exec(entry.name);
      const heading = match && new RegExp(`^## \\d{4}-\\d{2}-\\d{2} — ${match[2]}$`, 'm');
      if (!heading || !heading.test(history)) missing.push(entry.name);
    }
    return missing;
  }

  it('has a history entry for every archived change', () => {
    const missing = missingHistoryEntries();
    assert.deepEqual(missing, [], `docs/feature_history.md lacks entries for: ${missing.join(', ')}`);
  });
});

describe('CI parity', () => {
  // Decision 1.9: GitHub and GitLab must run the same build, test and image commands.
  const KEYWORDS = ['docker build', './mvnw', 'npm ci', 'npm run', 'node --test'];

  const ciCommands = (yamlText) => {
    const commands = new Set();
    for (const raw of yamlText.split('\n')) {
      const line = raw.trim().replace(/^- /, '').replace(/^(run|script):/, '').trim();
      if (line.startsWith('#') || !KEYWORDS.some((k) => line.includes(k))) continue;
      commands.add(
        line
          .replace(/^cd \S+ &&\s*/, '')
          .replace(/\$\{\{\s*github\.sha\s*\}\}|\$CI_COMMIT_SHORT_SHA/g, '<SHA>')
          .replace(/ --cache \.npm --prefer-offline/, '')
          .replace(/\s+/g, ' ')
      );
    }
    return commands;
  };

  const assertCiParity = (githubYaml, gitlabYaml) => {
    const github = ciCommands(githubYaml);
    const gitlab = ciCommands(gitlabYaml);
    const missing = [
      ...[...gitlab].filter((c) => !github.has(c)).map((c) => `missing on GitHub: ${c}`),
      ...[...github].filter((c) => !gitlab.has(c)).map((c) => `missing on GitLab: ${c}`),
    ];
    if (missing.length) assert.fail(`CI drift between forges:\n${missing.join('\n')}`);
  };

  it('runs the same build, test and image commands on GitHub and GitLab', () => {
    assertCiParity(
      fs.readFileSync('.github/workflows/ci.yml', 'utf8'),
      fs.readFileSync('.gitlab-ci.yml', 'utf8')
    );
  });

  it('names the missing command', () => {
    const github = 'steps:\n  - run: npm ci\n';
    const gitlab =
      'script:\n  - npm ci --cache .npm --prefer-offline\n  - docker build --pull -t "nifty-backend:$CI_COMMIT_SHORT_SHA" backend\n';
    assert.throws(
      () => assertCiParity(github, gitlab),
      (err) => err.message.includes('missing on GitHub: docker build --pull -t "nifty-backend:<SHA>" backend')
    );
  });
});
