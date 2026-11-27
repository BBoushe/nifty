## What changed?

<!-- 1–2 short, plain-language bullets readable in under 60 seconds. -->

-

## Why?

<!-- Problem solved + key Decision X.Y pointers (docs/decisions/index.md). -->

-

## Spec / Issue Reference

- OpenSpec change (if applicable): `openspec/changes/<name>/`
- Related issue / Jira key:
- Suggested reviewer (`docs/workflow.md` Module Ownership Table):

## Verification

<!-- List ONLY commands actually executed with passing exit codes. -->

- Commands run: ``
- [ ] Backend tests pass (`cd backend && ./mvnw test`) — if `backend/` changed
- [ ] Frontend checks pass (`cd frontend && npm run typecheck && npm run lint && npm run build`) — if `frontend/` changed
- [ ] Harness / OpenSpec checks pass (`node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` & `openspec validate --all`) — if harness/specs changed
- [ ] Domain invariants (`AGENTS.md` §2) & living coding standards (`.agents/rules/coding-standards.md`) verified
