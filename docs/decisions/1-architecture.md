# Category 1 — Architecture Decisions (`1.x`)

System architecture, stack pins, LLM infrastructure, persistence, and forge/CI topology. See [index.md](./index.md) for the full registry.

---

## 1.1 — LLM in team formation (fresh context only)
- **Legacy ID:** D7
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Team Builder Modes B (text -> constraints) and C (validated LLM proposal) involve LLMs alongside deterministic Timefold solving.
- **Decision:** Run Team Builder LLM calls exclusively in a fresh, empty context (zero interview chat history) and validate every output deterministically in Java before applying.
- **Alternatives Considered:** Passing candidate transcripts into the team-formation prompt; pure LLM team assignment without solver validation.
- **Rationale / Justification:** Eliminates hallucinated candidates, duplicate placements, or invented scores while preserving EU AI Act auditability.
- **Affected Files / Pointers:** `AGENTS.md`, `openspec/specs/team-builder/spec.md`, `docs/architecture.md`

## 1.2 — Assessment Transcript format (`assessment-transcript/v1`)
- **Legacy ID:** D8
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Interview Q/A pairs, metadata, and extracted evidence quotes must be stored, validated, and exported cleanly across backend, frontend, and LLMs.
- **Decision:** Use JSON validated by JSON Schema (`assessment-transcript/v1`, `*.transcript.json`, PostgreSQL `JSONB`) instead of a custom `.ast` DSL.
- **Alternatives Considered:** Custom `.ast` text DSL; fully normalized relational tables for every turn without JSONB snapshot.
- **Rationale / Justification:** Native Jackson, TypeScript, PostgreSQL JSONB, and LLM structured-output support within a 1-week block week.
- **Affected Files / Pointers:** `docs/architecture.md`, `openspec/specs/assessment-chat/spec.md`, `openspec/specs/evidence-pipeline/spec.md`

## 1.3 — Core tech stack
- **Legacy ID:** D11
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** A 5-person team across Windows, macOS, and Linux needs a stable, course-aligned full-stack foundation for a 1-week sprint.
- **Decision:** Java 21 + Spring Boot 4.x + Spring AI 2.0 + Timefold Solver + PostgreSQL + Flyway; Vue 3.5 (**stable only — never 3.6 RC**) + TypeScript + Vite 8 + Pinia + Vue Router + TanStack Query + Tailwind CSS v4 + Apache ECharts.
- **Alternatives Considered:** Vue 3.6 RC / Vapor Mode; Node/Python backend.
- **Rationale / Justification:** Aligns with course mandate, 2026–2027 production stability, Spring AI provider portability, and deterministic Java constraint solving.
- **Affected Files / Pointers:** `backend/pom.xml`, `frontend/package.json`, `AGENTS.md`, `openspec/config.yaml`

## 1.4 — Tool-agnostic spec-driven harness
- **Legacy ID:** D12
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Team members use different AI tools and IDEs (Claude Code, Antigravity `agy`, Codex, Kiro Crew, T3 Code, plain IDEs).
- **Decision:** Store OpenSpec (`openspec/`), universal rules (`AGENTS.md`, `.agents/rules/`), thin tool pointers (`CLAUDE.md`, `GEMINI.md`, `.kiro/steering/`), and portable hooks (`.githooks/`, `.claude/hooks/*.mjs`) as plain files in Git.
- **Alternatives Considered:** Locking into a single proprietary IDE/wrapper workspace.
- **Rationale / Justification:** Zero vendor lock-in and identical domain/security guardrails across every developer and AI tool.
- **Affected Files / Pointers:** `AGENTS.md`, `openspec/config.yaml`, `docs/setup.md`, `.githooks/`

## 1.5 — Default branch protection (`master` & `main`)
- **Legacy ID:** D13
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** The remote repository currently uses `master` as its default branch alongside `alpha` and `dev`.
- **Decision:** Protect both `master` and `main` (via `~DEFAULT_BRANCH` + explicit refs) across GitHub Rulesets, CI, `.githooks/pre-push`, and `.claude/hooks/guard-bash.mjs`.
- **Alternatives Considered:** Protecting only `main` or forcing an immediate default-branch rename.
- **Rationale / Justification:** Matches the live remote without breaking scripts or future branch renames.
- **Affected Files / Pointers:** `.github/rulesets/`, `.github/workflows/ci.yml`, `.gitlab-ci.yml`, `.githooks/pre-push`, `.claude/hooks/guard-bash.mjs`

## 1.6 — EU LLM evaluator provider (`ki:connect` NRW / FH AI)
- **Legacy ID:** D14
- **Date:** 2026-11-22
- **Triggered By:** prompter-change
- **Context:** Evidence extraction and constrained judging require an EU-hosted, GDPR-friendly, student-accessible OpenAI-compatible LLM endpoint, while keeping future Gemini/Claude/OpenAI portability via Spring AI 2.0.
- **Decision:** Configure **ki:connect NRW / FH AI** (`https://chat.kiconnect.nrw/api/v1`, hosted in Aachen / NRW, free for students) via `KICONNECT_API_KEY` for the LLM evaluator (models: `OpenAI GPT OSS 120B`, `Qwen 3.8 27B`, `Mistral Small 4 119B`). Do **not** build an artificial offline stub gateway; mock/replay Spring AI `ChatClient` in tests.
- **Alternatives Considered:** Direct US-hosted OpenAI API; custom `FakeCloudProvider` offline demo stub.
- **Rationale / Justification:** EU data residency in NRW, zero student cost, and native Spring AI 2.0 multi-provider portability without redundant wrapper layers.
- **Affected Files / Pointers:** `.env.example`, `docker-compose.yml`, `backend/src/main/resources/application.yml`, `.agents/rules/backend-java.md`

## 1.7 — Parallel GitHub (`gh`) / GitLab (`glab`) forge switch via `.env`
- **Date:** 2026-11-23
- **Triggered By:** prompter-change
- **Context:** Course delivery requires GitLab readiness alongside our existing GitHub repository without breaking `.env` secret isolation for AI agents.
- **Decision:** Add `GIT_FORGE=github|gitlab` (default `github`) and `GITLAB_HOST=gitlab.com` in `.env.example`, maintain parity `.gitlab-ci.yml` alongside `.github/workflows/ci.yml`, and route all agent/bootstrap forge checks through zero-dependency `scripts/check-setup.mjs` (which reads only non-secret allowlisted keys from `.env`).
- **Alternatives Considered:** Replacing GitHub entirely; letting AI agents read `.env` directly.
- **Rationale / Justification:** Enables seamless switching to university GitLab (`glab`) while keeping GitHub working and preserving airtight `.env` API-key protection.
- **Affected Files / Pointers:** `.env.example`, `scripts/check-setup.mjs`, `.gitlab-ci.yml`, `.agents/skills/pr-memo/SKILL.md`, `CLAUDE.md`

## 1.8 — Dual forge: GIT_FORGE=both with GitHub primary and GitLab secondary mirror
- **Date:** 2026-11-23
- **Triggered By:** prompter-change
- **Context:** The team started on GitHub but the course requires GitLab (git.inf.fh-dortmund.de/01/alpac001/nifty). Every push and PR must exist on both, and the repos must not drift.
- **Decision:** Add GIT_FORGE=both. Remote origin = GitHub (primary, where PRs are merged), remote gitlab = GitLab (secondary). scripts/forge.mjs pushes to both (GitHub first, stop on failure), opens PR and MR with identical text, and fast-forwards GitLab dev/alpha/master after GitHub merges (sync, no force). scripts/apply-gitlab-protections.sh mirrors .github/rulesets/.
- **Alternatives Considered:** One origin with two push URLs (gh/glab cannot tell the host, failures are silent); GitLab push mirroring or a GitHub Action mirror (tier and network access to the uni GitLab unknown); merging on both sides (guaranteed drift).
- **Rationale / Justification:** Two named remotes keep gh and glab unambiguous, GitHub-first ordering stops GitLab from getting ahead, and merge-only-on-GitHub plus fast-forward sync removes divergence without force-pushes.
- **Affected Files / Pointers:** scripts/forge.mjs, scripts/apply-gitlab-protections.sh, scripts/check-setup.mjs, .agents/skills/pr-memo/SKILL.md, docs/workflow.md

## 1.9 — CI parity: GitHub CI gates the same jobs as GitLab CI
- **Date:** 2026-11-24
- **Triggered By:** prompter-change
- **Context:** Merges happen only on GitHub, but GitLab CI also builds both Docker images. A Dockerfile regression can pass GitHub, merge, and fail on GitLab after it is in dev. Hook Tests (Node) is not a required GitHub check.
- **Decision:** GitHub CI runs the same docker build commands as GitLab (jobs Backend Image (Docker), Frontend Image (Docker)). Rulesets for dev, alpha and main require these two checks and Hook Tests (Node). A zero-dependency drift test in scripts/harness-scripts.test.mjs fails when the command sets of the two CI files differ.
- **Alternatives Considered:** Wait for GitLab manually before merging (error-prone); move merges to GitLab (contradicts Decision 1.8).
- **Rationale / Justification:** A green GitHub run must mean a green GitLab run, without a human remembering to wait for GitLab.
- **Affected Files / Pointers:** openspec/changes/forge-ci-parity/, .github/workflows/ci.yml, .github/rulesets/*.json

## 1.10 — Backend package-by-feature with ArchUnit-enforced boundaries
- **Date:** 2026-11-24
- **Triggered By:** prompter-change
- **Context:** The course guide (Spring Boot Architecture Best Practices) recommends package-by-feature, strict DTOs, RFC 9457 errors, commit-safe events and layered tests. The backend is still a skeleton (config/, health/), so the layout is cheap to fix now.
- **Decision:** Backend code lives in de.fhdortmund.nifty.shared.{config,exception} or de.fhdortmund.nifty.<feature>.{web,service,repository}. ArchUnit tests enforce layer and cross-feature rules (no foreign repository access, web never touches repository, shared never depends on a feature, no @Entity in controller signatures). One GlobalExceptionHandler returns RFC 9457 ProblemDetail. Tests stay on H2 for the context smoke test; PostgreSQL Testcontainers arrive with the first @Entity. Domain events use @TransactionalEventListener(AFTER_COMMIT) when a feature first needs them.
- **Alternatives Considered:** Rules in backend-java.md only (not enforced); switch tests to Testcontainers now (couples to CI changes before any entity exists).
- **Rationale / Justification:** Feature slices keep the blast radius local and let agents read one folder. ArchUnit turns written rules into a failing test, which raises first-time quality for AI-written code.
- **Affected Files / Pointers:** openspec/changes/backend-architecture-baseline/, .agents/rules/backend-java.md, docs/architecture.md

## 1.11 — Answers stream the interviewer reply; append-only ledger
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** PDF 4/5 suggest a synchronous POST .../turn endpoint without streaming. The team wants the interview to feel like an LLM conversation and to log every message for transparency.
- **Decision:** POST /api/v1/assessment/sessions starts a session and POST /api/v1/assessment/sessions/{id}/answers sends one answer; both respond with an SSE stream of the interviewer reply. GET /api/v1/assessment/sessions/{id} returns the current state from the ledger, so a page refresh resumes the session. Every student answer and every AI reply is appended to the ledger (an append-only PostgreSQL table with server timestamps). When the server asks a question, it stores expiresAt and sends it to the browser; the browser counts down and auto-submits the current text at zero. The server rejects an answer after expiresAt plus a short grace period with 409 Conflict, and an answer after expiresAt is recorded with timedOut = true (the question is N/A, Decision 2.14). The session state machine enforces question order, time limits and the follow-up limit in Java. assessment-transcript/v1 is built from the ledger.
- **Alternatives Considered:** One long-lived SSE stream per session plus POST answers returning 202; WebSocket; synchronous POST /turn without streaming (PDF 4/5); a single JSONB transcript updated in place (Decision 1.2).
- **Rationale / Justification:** Simplest reliable build: no long-lived connection to re-attach after a refresh, streamed replies keep the conversation feel, and server-stored deadlines keep the timers cheat-proof.
- **Affected Files / Pointers:** docs/architecture.md, docs/api.md, openspec/specs/assessment-chat/spec.md

## 1.12 — Dual-agent rubric isolation
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** PDF 5 separates a rubric-blind interviewer from an asynchronous evaluator, so a candidate cannot prompt-inject the grader.
- **Decision:** Agent 1 (interviewer) never receives rubrics, level descriptions or scores. It stays on topic, reveals no answers or scores, does not judge the person, and keeps its interviewer role. Agent 2 (evaluator) runs after the session is committed (ApplicationEventPublisher + @TransactionalEventListener(AFTER_COMMIT)) and reads only the ledger plus the rubric. It returns evidence items, never totals; Java verifies quotes and computes confidence and scores.
- **Alternatives Considered:** One agent that interviews and grades.
- **Rationale / Justification:** Prompt injection during the interview cannot reach the grading context.
- **Affected Files / Pointers:** AGENTS.md, docs/architecture.md, openspec/specs/assessment-chat/spec.md, openspec/specs/evidence-pipeline/spec.md

## 1.13 — Interviewer uses ki:connect NRW
- **Resolves:** O2
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** O2 asked whether the interviewer also uses ki:connect NRW (1.6) or a separate EU endpoint.
- **Decision:** The interviewer also uses ki:connect NRW through the Spring AI ChatClient, streamed.
- **Alternatives Considered:** A separate EU-hosted streaming endpoint.
- **Rationale / Justification:** One provider, one key, EU hosting.
- **Affected Files / Pointers:** docs/architecture.md, backend/src/main/resources/application.yml

## 1.14 — PrimeVue on Tailwind v4; design system uses 0-4 and hatched N/A
- **Resolves:** O1
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** O1 asked for the UI kit. docs/design_system.md uses a 0-100 scale, 4 levels, no N/A and plain CSS.
- **Decision:** The UI kit is PrimeVue, themed from our design tokens on Tailwind v4. The design system uses the 0-4 scale with a hatched N/A state and keeps its visual identity (colours, AI sparkle, type, 4px grid).
- **Alternatives Considered:** shadcn-vue; plain CSS bundle from the design system draft.
- **Rationale / Justification:** Ready DataTable, Slider, Drawer and Dialog components save block-week time. 0-4 + N/A matches the domain model.
- **Affected Files / Pointers:** docs/design_system.md, frontend/package.json, .agents/rules/frontend-vue.md

## 1.15 — Two roles (PROFESSOR, CANDIDATE), easy to extend
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** Role.java has PROFESSOR, REVIEWER and CANDIDATE; the docs say 2 roles and give REVIEWER professor-only routes.
- **Decision:** MVP has two roles: PROFESSOR and CANDIDATE. The professor does all review work. Route access lives in one place (SecurityConfig plus method-level @PreAuthorize), so a new role such as REVIEWER is one enum value and a few rules.
- **Alternatives Considered:** Keep three roles now.
- **Rationale / Justification:** Fewer roles to build and test in block week, with a clear path to add more.
- **Affected Files / Pointers:** backend/src/main/java/de/fhdortmund/nifty/shared/config/Role.java, docs/api.md, AGENTS.md

## 1.16 — Backend slice map: auth and audit slices; studio owns inferred tags
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** PDF 2 asks for coarse product slices. Authentication has no slice, audit_log has no owner, and inferredTags ownership differs across 3 documents.
- **Decision:** Add an auth slice (accounts, login, candidate invite links) and an audit slice (AuditLog entity and a public AuditService; other slices call the service, never the repository). studio owns the skill taxonomy and inferredTags; evidence proposes tags through studio's service. The BFI-10 answers and trait scores belong to assessment. The other slices stay as they are.
- **Alternatives Considered:** Audit writes from each slice; inferredTags in evidence; merge results into assessment.
- **Rationale / Justification:** Each slice has one owner, which allows parallel work without merge conflicts and keeps the ArchUnit repository rule.
- **Affected Files / Pointers:** docs/architecture.md, docs/api.md, openspec/specs/interviewer-studio/spec.md

## 1.17 — Students join by invite link only
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** The auth slice needs a student sign-in model. Students take one assessment and have no other use for an account.
- **Decision:** The professor creates invite links (CSV import or one at a time). Each link holds a personal, unguessable token bound to one assessment and one student. Opening it starts an authenticated CANDIDATE session; the student can reopen the same link until the attempts run out or the professor revokes it. Students have no passwords. Professors sign in with account credentials. The token format (JWT or server session) is decided in the auth change design.
- **Alternatives Considered:** Student accounts with email and password registration.
- **Rationale / Justification:** No registration or password reset to build in block week, and every student is tied to exactly one assessment.
- **Affected Files / Pointers:** openspec/specs/auth-access/spec.md, docs/api.md
