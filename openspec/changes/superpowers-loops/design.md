# Design: Superpowers Loops, Resumable Tasks & Archive Gate

## 1. Plugin Declaration

- Find the exact plugin key with `claude plugin list` (or from the developer's `~/.claude/settings.json` `enabledPlugins`, read-only). The expected form is `"superpowers@claude-plugins-official": true`.
- Confirm with the `claude-code-guide` agent how project-level `enabledPlugins` behaves: does it prompt to install, and is `extraKnownMarketplaces` needed for the official marketplace? Write the answer into the Handoff Log.
- Add only Superpowers. Other personal plugins stay per developer.

## 2. Loop in Role Prompts

Write the loop once in `.claude/agents/spec-builder.md`, and point `.claude/skills/spec-implementer/SKILL.md` and `test-writer.md` to it (DRY). Each step names the Superpowers skill and its inline fallback for other tools:

| Step | Claude Code skill | Inline fallback (all tools) |
|---|---|---|
| Before proposing a change | `superpowers:brainstorming` / `openspec-explore` | Ask clarifying questions; list edge cases; compare 2 options. |
| Each code task | `superpowers:test-driven-development` | Write the failing test, run it, paste the failure; write the minimal code; run it green; refactor; run it again. |
| Any failure | `superpowers:systematic-debugging` | Read the error, trace the callers, state a hypothesis, write a reproducing test, fix the root cause. |
| Before `[x]` | `superpowers:verification-before-completion` | Run the task's verification command and check its exit code. |
| Every stop | — | Overwrite `## Status`; commit and push after each finished task. After the **last** task, run the Delivery archive step before pushing (the pre-push gate blocks the push otherwise). |

Handoff Log Verification line format: `RED: <cmd> → FAIL (<one-line reason>); GREEN: <cmd> → exit 0`. Doc-only or config-only tasks write `RED: n/a (no behaviour)`.

`reviewer.md`: add the checks "code task without RED evidence → Blocking" and "Status block stale after the last commit → Convention Candidate".

## 3. `openspec/config.yaml`

Extend `rules.tasks` and `operations.apply.guidance`:

- `tasks.md` layout: `## Status` (overwritten; Branch, Worktree, Current, Next action, Uncommitted changes, PR / MR, Open review findings), `## Tasks` with nested step checkboxes, `## Delivery` (rebase, archive, push, PR, review, CI, merge, sync, cleanup), `## Handoff Log`.
- Never add an "archive" task above `## Delivery`.
- The RED/GREEN Verification line format from §2.

## 4. Archive Gate (`.claude/hooks/archive-gate.mjs`)

It does not live in `lib.mjs` (owned by `bash-guard-hardening`). It may import `readStdinJson` and `getRepoRoot` from `lib.mjs`, unchanged.

```text
export function findCompletedUnarchivedChanges(repoRoot) -> string[]
  for each dir in openspec/changes/* except "archive":
    text = tasks.md (skip the dir if missing)
    head = text before the first line matching /^## Delivery\b/m (whole text if absent)
    done = count /^\s*- \[x\]/gim in head; open = count /^\s*- \[ \]/gm in head
    if done > 0 and open == 0: push dir name
  return sorted names
```

`main()` modes:

- `--stop-hook`: read stdin JSON. If `stop_hook_active` is true, exit 0 silently. Otherwise, if the list is non-empty, print `{"decision":"block","reason":"Archive completed OpenSpec change(s): <names>. Run: OPENSPEC_TELEMETRY=0 openspec archive <name> -y --skip-specs (domain changes: sync specs first), then add the docs/feature_history.md entry."}`. Exit 0 in all cases; a crash must never block a session. Confirm the Stop hook output schema with `claude-code-guide`.
- `--pre-push`: print the same message to stderr and exit 1 if the list is non-empty. `NIFTY_SKIP_GATE=1` skips the check.

`.githooks/pre-push`: inside the existing `while read` loop, run the gate once for the first `remote_ref` that is **not** `refs/heads/{dev,alpha,master,main}`. Mirror syncs and protected pushes never run it.

Register it in `.claude/settings.json` under `hooks.Stop` → `node .claude/hooks/archive-gate.mjs --stop-hook`.

## 5. Tests (new `describe('archive gate')` at the end of `.claude/hooks/hooks.test.mjs`)

- Fixture repos in `fs.mkdtempSync`:
  - all `[x]` → listed;
  - one `[ ]` above Delivery → not listed;
  - `[ ]` only below Delivery → listed;
  - no checkboxes → not listed;
  - a folder under `archive/` → ignored;
  - a missing `tasks.md` → ignored.
- `--stop-hook`: spawn with stdin `{}` against a fixture root (pass the root through an env var, `NIFTY_REPO_ROOT`, used only by tests) → block JSON. With `{"stop_hook_active":true}` → no output.
- Repo invariant: `findCompletedUnarchivedChanges(getRepoRoot())` deep-equals `[]`. This runs in CI on both forges without a CI file change.
