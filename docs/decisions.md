# Architectural & Product Decisions

This log captures the baseline architectural decisions from [`docs/discovery-canvas.md`](./discovery-canvas.md) (§12–§13) and tracks open team decisions.

## 1. Decided Architecture & Product Rules

| ID | Topic | Decision | Rationale |
|---|---|---|---|
| **D1** | Inferred tags from answers | Extractor suggests tags as `pending`; professor approves, merges, or rejects into controlled list. | Prevents tag sprawl (e.g., "Docker" vs. "containers") and keeps cross-candidate calibration consistent. |
| **D2** | Multi-skill answers | One Q/A pair may produce evidence for multiple skills, each with its own level and confidence; default formula caps single-Q/A impact per skill. | Reflects realistic conversational answers without allowing one long answer to dominate all scores. |
| **D3** | Question & skill weights | Both exist (`w_q` and `w_s`); Evidence Drawer displays every multiplication step. | Full transparency for professors and candidates. |
| **D4** | Invented questions | Optional via Studio toggle; tagged `origin: invented`, lower default weight, optionally excluded from cross-candidate ranking. | Preserves cohort comparability while allowing adaptive probing. |
| **D5** | Turn budget vs. skill count | Core questions guarantee baseline coverage; untested skills record `N/A`. | Prevents penalizing candidates for unasked domains. |
| **D6** | Post-interview config changes | Weight/formula changes recompute immediately; adding skills or core questions creates a new config version and flags affected candidates as incomplete. | Protects evidence integrity and auditability. |
| **D7** | LLM in team formation | Only in a fresh, empty context: parses natural-language criteria into Timefold constraints (Mode B) or proposes teams validated by deterministic code (Mode C). | Eliminates hallucinated candidates, duplicate placements, or invented scores. |
| **D8** | Assessment Transcript format | JSON validated by JSON Schema (`assessment-transcript/v1`, `*.transcript.json`, Postgres `JSONB`), not a custom `.ast` DSL. | Native Jackson, TypeScript, JSONB, and LLM structured-output support within a 1-week block week. |
| **D9** | "Ranking View" UX framing | Internal module name stays "Ranking View"; UI emphasizes spec fit and team balance over a single global leaderboard. | Reduces EU AI Act / discrimination liability and better serves team formation. |
| **D10** | Candidate transparency | Formula, weights, and verified evidence are shown to the candidate **only after human approval**; mocked dispute flow in MVP. | Prevents gaming during interviews while providing full post-approval explainability. |
| **D11** | Core stack | Java 21 + Spring Boot 4.x + Spring AI 2.0 + Timefold Solver + PostgreSQL + Flyway; Vue 3.5 (stable, **not** 3.6 RC) + TypeScript + Vite 8 + Pinia + Vue Router + TanStack Query + Tailwind CSS v4 + Apache ECharts. | Aligns with course mandate, 2026–2027 production stability, and deterministic Java constraint solving. |
| **D12** | Spec-driven harness | OpenSpec (`openspec/`) + universal `AGENTS.md` + agent-native `CLAUDE.md`/`GEMINI.md`/`.agents/rules/` + portable `.githooks/`. | Tool-agnostic across T3 Code, Claude Code, Antigravity (`agy`), Codex, and IDEs. |
| **D13** | Default branch protection | Repository currently uses `master` as its default branch alongside `alpha` and `dev`. Governance rulesets, hooks, and CI protect both `master` and `main` (via `~DEFAULT_BRANCH` + explicit refs) to stay resilient if the default branch is renamed. | Matches existing Git remote without breaking docs or future renames. |

## 2. Open Team Decisions (TBD)

These items are intentionally left open for the 5-person team to finalize on Day 1:

| ID | Question | Options / Constraints | Owner |
|---|---|---|---|
| **O1** | **Frontend UI component library** | **PrimeVue** (rich data tables/sliders out of the box) vs. **shadcn-vue** (copy-paste Radix/Reka primitives on Tailwind CSS v4). | Frontend leads |
| **O2** | **LLM provider & EU region** | Must prefer **EU-hosted endpoints** (GDPR: transcripts are personal data) and support Spring AI 2.0 structured output & streaming (e.g., Azure OpenAI EU, Mistral EU, OpenAI EU data residency, Vertex AI `europe-west*`, or Ollama for local dev). | Backend / AI lead |
| **O3** | **MVP skill taxonomy size** | Keep all 16 course sub-skills or trim to 6–8 core project-readiness skills for block-week velocity. | Whole team + Lecturer |
| **O4** | **Team size & solver constraints** | Confirm lecturer's exact team size (default 5) and hard/soft constraints (e.g., ≥1 strong backend/frontend per team). | Team Builder lead + Lecturer |
| **O5** | **Formula expression editor in MVP** | Ship presets only in MVP or include the sandboxed SpEL expression editor. | Formula Engine lead |
| **O6** | **Candidate result visibility timing** | Show results immediately after individual score approval, or only after final teams are published? | Whole team |
| **O7** | **Demo transcript data retention** | Define retention / purge window for real or synthetic transcripts in the demo environment. | Whole team |
