import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { evaluateAgentAction, isAllowedTestPath } from './guard-agent.mjs';
import { evaluateBashCommand, evaluatePreCommit } from './guard-bash.mjs';
import { evaluateFileTool } from './guard-files.mjs';
import {
  findSecretsInDiff,
  isBlockedEnvPath,
  splitShellSegments,
} from './lib.mjs';
import { planGateCommands } from './test-gate.mjs';

const FAKE_OPENAI_KEY = ['sk', 'proj', 'A'.repeat(24)].join('-');
const FAKE_ANTHROPIC_KEY = ['sk', 'ant', 'api03', 'B'.repeat(20)].join('-');
const FAKE_GOOGLE_KEY = `AIza${'C'.repeat(35)}`;
const FAKE_GH_PAT = `ghp_${'D'.repeat(28)}`;
const FAKE_PEM_HEADER = ['-----BEGIN', 'RSA PRIVATE', 'KEY-----'].join(' ');

describe('lib.mjs helpers', () => {
  it('blocks .env and .env.* paths while allowing .env.example', () => {
    assert.equal(isBlockedEnvPath('.env'), true);
    assert.equal(isBlockedEnvPath('./.env'), true);
    assert.equal(isBlockedEnvPath('backend/.env.local'), true);
    assert.equal(isBlockedEnvPath('C:\\repo\\.env.production'), true);
    assert.equal(isBlockedEnvPath('**/.env*'), true);

    assert.equal(isBlockedEnvPath('.env.example'), false);
    assert.equal(isBlockedEnvPath('./.env.example'), false);
    assert.equal(isBlockedEnvPath('backend/.env.example'), false);
    assert.equal(isBlockedEnvPath('docs/setup.md'), false);
  });

  it('splits shell commands on unquoted operators without breaking quoted text', () => {
    const segs = splitShellSegments(
      'git add README.md && git commit -m "fix: handle a && b; c" || echo done'
    );
    assert.deepEqual(segs, [
      'git add README.md',
      'git commit -m "fix: handle a && b; c"',
      'echo done',
    ]);
  });

  it('detects secrets only on added diff lines and ignores placeholders', () => {
    const cleanDiff = [
      '+++ b/.env.example',
      '+KICONNECT_API_KEY=',
      '+KICONNECT_API_KEY=not-configured',
      '+- Documented placeholder `KICONNECT_API_KEY=<real-value>` in markdown',
      '+SPRING_DATASOURCE_PASSWORD=nifty',
      `-${FAKE_OPENAI_KEY}`,
    ].join('\n');
    assert.equal(findSecretsInDiff(cleanDiff).length, 0);

    const leakyDiff = [
      '+++ b/backend/config.yml',
      `+apiKey: ${FAKE_OPENAI_KEY}`,
      `+claude: ${FAKE_ANTHROPIC_KEY}`,
      `+gemini: ${FAKE_GOOGLE_KEY}`,
      `+gh: ${FAKE_GH_PAT}`,
      `+${FAKE_PEM_HEADER}`,
      `+KICONNECT_API_KEY=${['fhai', 'live', 'real', 'secret', '999'].join('_')}`,
    ].join('\n');
    const findings = findSecretsInDiff(leakyDiff);
    assert.equal(findings.length, 6);
    for (const f of findings) {
      assert.equal(f.preview.includes('AAAA'), false, 'redacted preview must not leak secret chars');
      assert.equal(f.preview.includes('sk-'), false, 'redacted preview must not leak key prefix');
      assert.equal(f.preview.includes('[REDACTED]'), true);
    }
  });
});

describe('guard-files.mjs', () => {
  it('blocks Read, Edit, Write, MultiEdit, Grep, Glob on .env files', () => {
    for (const payload of [
      { tool_name: 'Read', tool_input: { file_path: '.env' } },
      { tool_name: 'Edit', tool_input: { file_path: 'backend/.env.local' } },
      { tool_name: 'Write', tool_input: { file_path: '.env.production' } },
      { tool_name: 'MultiEdit', tool_input: { edits: [{ file_path: '.env' }] } },
      { tool_name: 'Grep', tool_input: { path: '.env', pattern: 'KEY' } },
      { tool_name: 'Grep', tool_input: { glob: '.env*', pattern: 'KEY' } },
      { tool_name: 'Glob', tool_input: { pattern: '**/.env*' } },
    ]) {
      const res = evaluateFileTool(payload);
      assert.equal(res.blocked, true, `expected block for ${JSON.stringify(payload)}`);
    }
  });

  it('allows .env.example, regular source files, and Grep searching docs for ".env" text', () => {
    for (const payload of [
      { tool_name: 'Read', tool_input: { file_path: '.env.example' } },
      { tool_name: 'Edit', tool_input: { file_path: '.env.example' } },
      { tool_name: 'Grep', tool_input: { path: 'backend/src', pattern: 'KICONNECT' } },
      { tool_name: 'Grep', tool_input: { path: 'docs', pattern: '.env' } },
      { tool_name: 'Glob', tool_input: { pattern: 'frontend/src/**/*.vue' } },
    ]) {
      const res = evaluateFileTool(payload);
      assert.equal(res.blocked, false, `expected allow for ${JSON.stringify(payload)}`);
    }
  });
});

describe('guard-bash.mjs', () => {
  const cleanOpts = {
    currentBranch: 'feat/new-harness-tools',
    stagedDiff: '+++ b/README.md\n+Updated docs',
    stagedFiles: ['README.md'],
  };

  it('blocks reading, printing, or staging .env via shell including subshells', () => {
    for (const cmd of [
      'cat .env',
      'grep KEY .env.local',
      'git add .env',
      'head -n 5 < .env',
      'source .env',
      'echo $(cat .env)',
      'echo "$(<.env)"',
      'printf "%s" "`cat .env.local`"',
    ]) {
      const res = evaluateBashCommand(cmd, cleanOpts);
      assert.equal(res.blocked, true, `expected block for '${cmd}'`);
    }
  });

  it('sees through the rtk wrapper so rewritten commands stay guarded', () => {
    for (const cmd of [
      'rtk git push origin master',
      'rtk proxy git push origin master',
      'rtk -v bypass git push --force origin x',
      '/opt/homebrew/bin/rtk git add a && rtk git commit -m "docs: x"',
      'rtk read .env',
      'rtk git commit -am "docs: bypass"',
    ]) {
      assert.equal(evaluateBashCommand(cmd, cleanOpts).blocked, true, `expected block for '${cmd}'`);
    }
    assert.equal(evaluateBashCommand('rtk git status', cleanOpts).blocked, false);
  });

  it('allows reading .env.example, plain echo text, and commit messages mentioning .env', () => {
    assert.equal(evaluateBashCommand('cat .env.example', cleanOpts).blocked, false);
    assert.equal(evaluateBashCommand('echo "Copy .env.example to .env"', cleanOpts).blocked, false);
    assert.equal(
      evaluateBashCommand('git commit -m "chore(hooks): block .env reads"', cleanOpts).blocked,
      false
    );
  });

  it('blocks chaining git add && git commit and git commit -a/-am/--all', () => {
    assert.equal(
      evaluateBashCommand('git add README.md && git commit -m "docs: update"', cleanOpts).blocked,
      true
    );
    assert.equal(
      evaluateBashCommand('git commit -am "docs: bypass"', cleanOpts).blocked,
      true
    );
    assert.equal(
      evaluateBashCommand('git commit --all -m "docs: bypass"', cleanOpts).blocked,
      true
    );
  });

  it('blocks direct commits and pushes on master, main, alpha, and force pushes', () => {
    assert.equal(
      evaluateBashCommand('git commit -m "feat: oops"', { ...cleanOpts, currentBranch: 'master' })
        .blocked,
      true
    );
    assert.equal(
      evaluateBashCommand('git push origin master', cleanOpts).blocked,
      true
    );
    assert.equal(
      evaluateBashCommand('git push origin feat/x:refs/heads/alpha', cleanOpts).blocked,
      true
    );
    assert.equal(
      evaluateBashCommand('git push --force origin feat/x', cleanOpts).blocked,
      true
    );
    assert.equal(
      evaluateBashCommand('git push --all', cleanOpts).blocked,
      true
    );
    // Safe chained command should NOT be falsely blocked
    assert.equal(
      evaluateBashCommand('git push origin feat/new-harness-tools && git status', cleanOpts)
        .blocked,
      false
    );
  });

  it('enforces docs/hotfix-only rule on direct pushes to dev', () => {
    const blockedDev = evaluateBashCommand('git push origin dev', {
      ...cleanOpts,
      pushCommitSubjects: ['docs(readme): update', 'feat(backend): add endpoint'],
    });
    assert.equal(blockedDev.blocked, true);

    const allowedDev = evaluateBashCommand('git push origin dev', {
      ...cleanOpts,
      pushCommitSubjects: ['docs(readme): update', 'hotfix(ci): fix yaml'],
    });
    assert.equal(allowedDev.blocked, false);
  });

  it('blocks staged secrets and staged .env files in pre-commit', () => {
    const secretCommit = evaluatePreCommit({
      currentBranch: 'feat/new-harness-tools',
      stagedFiles: ['backend/src/main/resources/application.yml'],
      stagedDiff: `+++ b/backend/src/main/resources/application.yml\n+api-key: ${FAKE_OPENAI_KEY}`,
    });
    assert.equal(secretCommit.blocked, true);

    const envCommit = evaluatePreCommit({
      currentBranch: 'feat/new-harness-tools',
      stagedFiles: ['.env'],
      stagedDiff: '',
    });
    assert.equal(envCommit.blocked, true);
  });
});

describe('guard-agent.mjs', () => {
  it('blocks all edits and mutating bash commands in readonly mode', () => {
    assert.equal(
      evaluateAgentAction({ tool_name: 'Edit', tool_input: { file_path: 'README.md' } }, 'readonly')
        .blocked,
      true
    );
    assert.equal(
      evaluateAgentAction({ tool_name: 'Bash', tool_input: { command: 'rm -rf tmp' } }, 'readonly')
        .blocked,
      true
    );
    assert.equal(
      evaluateAgentAction(
        { tool_name: 'Bash', tool_input: { command: 'echo x > file.txt' } },
        'readonly'
      ).blocked,
      true
    );
    assert.equal(
      evaluateAgentAction(
        { tool_name: 'Bash', tool_input: { command: 'git -C . reset --hard' } },
        'readonly'
      ).blocked,
      true
    );
    assert.equal(
      evaluateAgentAction(
        { tool_name: 'Bash', tool_input: { command: 'git -c user.name=test commit -m "x"' } },
        'readonly'
      ).blocked,
      true
    );
    assert.equal(
      evaluateAgentAction(
        { tool_name: 'Bash', tool_input: { command: 'git diff dev...HEAD' } },
        'readonly'
      ).blocked,
      false
    );
  });

  it('restricts writes to test paths only in tests-only mode and blocks path traversal', () => {
    assert.equal(isAllowedTestPath('backend/src/test/java/com/nifty/QuoteVerifierTest.java'), true);
    assert.equal(isAllowedTestPath('frontend/src/views/RankingView.spec.ts'), true);
    assert.equal(isAllowedTestPath('frontend/src/utils/format.test.ts'), true);

    assert.equal(isAllowedTestPath('backend/src/main/java/com/nifty/QuoteVerifier.java'), false);
    assert.equal(isAllowedTestPath('backend/src/test/../../main/java/App.java'), false);
    assert.equal(isAllowedTestPath('../outside/backend/src/test/AppTest.java'), false);
    assert.equal(isAllowedTestPath('frontend/src/views/RankingView.vue'), false);
    assert.equal(isAllowedTestPath('.env'), false);

    assert.equal(
      evaluateAgentAction(
        { tool_name: 'Write', tool_input: { file_path: 'backend/src/main/java/App.java' } },
        'tests-only'
      ).blocked,
      true
    );
    assert.equal(
      evaluateAgentAction(
        { tool_name: 'Write', tool_input: { file_path: 'backend/src/test/java/AppTest.java' } },
        'tests-only'
      ).blocked,
      false
    );
  });
});

describe('test-gate.mjs', () => {
  it('plans only path-scoped fast compile steps across POSIX and Windows', () => {
    assert.deepEqual(planGateCommands(['docs/setup.md', 'AGENTS.md']), []);

    const unixPlan = planGateCommands(
      ['frontend/src/App.vue', 'backend/src/main/java/App.java'],
      'darwin'
    );
    assert.equal(unixPlan.length, 3);
    assert.equal(unixPlan[0].cmd, 'npm');
    assert.equal(unixPlan[0].env.CI, 'true');
    assert.equal(unixPlan[2].cmd, './mvnw');
    assert.deepEqual(unixPlan[2].args, ['-q', 'test-compile']);

    const winPlan = planGateCommands(['backend/pom.xml'], 'win32');
    assert.equal(winPlan.length, 1);
    assert.equal(winPlan[0].cmd, 'mvnw.cmd');
  });
});
