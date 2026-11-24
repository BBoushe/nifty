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
