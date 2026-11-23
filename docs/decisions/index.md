# Decision Registry (`docs/decisions/`)

Every architectural, design, team-process, and product-direction decision lives here with a stable hierarchical ID (`<category>.<number>`, e.g., `1.2`, `2.4`) so code, specs, and Pull/Merge Requests stay traceable and justifiable.

## Code Pointer Convention

Whenever implementing non-obvious behavior or acting on a recorded decision, leave a concise inline pointer:
- **Java / TypeScript:** `// Decision 1.2: <short why>`
- **Vue templates / Markdown / SQL:** `<!-- Decision 2.4: <short why> -->` or `-- Decision 1.2: <short why>`

Record new decisions deterministically with:
```sh
node scripts/record-decision.mjs --category 1|2|3|4 --topic "..." --trigger "question-round|prompter-change|team" --context "..." --decision "..." --alternatives "..." --rationale "..." --pointers "..."
```

---

## 1. Master Decision Registry

| ID | Legacy ID | Category | Topic | Status | Date | Trigger | Details |
|---|---|---|---|---|---|---|---|
| **1.1** | D7 | Architecture | LLM in team formation (fresh context only) | Decided | 2026-11-22 | baseline | [1-architecture.md](./1-architecture.md#11--llm-in-team-formation-fresh-context-only) |
| **1.2** | D8 | Architecture | Assessment Transcript JSONB format (`assessment-transcript/v1`) | Decided | 2026-11-22 | baseline | [1-architecture.md](./1-architecture.md#12--assessment-transcript-format-assessment-transcriptv1) |
| **1.3** | D11 | Architecture | Core tech stack (Java 21 / Spring Boot 4 / Vue 3.5 stable) | Decided | 2026-11-22 | baseline | [1-architecture.md](./1-architecture.md#13--core-tech-stack) |
| **1.4** | D12 | Architecture | Tool-agnostic spec-driven harness (`openspec/` + `AGENTS.md`) | Decided | 2026-11-22 | baseline | [1-architecture.md](./1-architecture.md#14--tool-agnostic-spec-driven-harness) |
| **1.5** | D13 | Architecture | Default branch protection (`master` and `main` parity) | Decided | 2026-11-22 | baseline | [1-architecture.md](./1-architecture.md#15--default-branch-protection-master--main) |
| **1.6** | D14 | Architecture | EU LLM evaluator provider (`ki:connect` NRW / FH AI) | Decided | 2026-11-22 | prompter-change | [1-architecture.md](./1-architecture.md#16--eu-llm-evaluator-provider-kiconnect-nrw--fh-ai) |
| **1.7** | — | Architecture | Parallel GitHub (`gh`) / GitLab (`glab`) forge switch via `.env` | Decided | 2026-11-23 | prompter-change | [1-architecture.md](./1-architecture.md#17--parallel-github-gh--gitlab-glab-forge-switch-via-env) |
| **2.1** | D1 | Design | Inferred tags require human approval (`pending -> approved/rejected`) | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#21--inferred-tags-from-answers) |
| **2.2** | D2 | Design | Multi-skill answers with capped per-Q/A skill impact | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#22--multi-skill-answers) |
| **2.3** | D3 | Design | Separate question (`w_q`) and skill (`w_s`) weights shown in Evidence Drawer | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#23--question--skill-weights) |
| **2.4** | D4 | Design | Optional invented questions (`origin: invented`, lower default weight) | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#24--invented-questions) |
| **2.5** | D5 | Design | Turn budget vs. skill count (core baseline + explicit `N/A`) | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#25--turn-budget-vs-skill-count) |
| **2.6** | D6 | Design | Post-interview config changes (recompute weights vs. new config version) | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#26--post-interview-config-changes) |
| **2.7** | D9 | Design | Ranking View UX framing (spec fit & team balance over global leaderboard) | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#27--ranking-view-ux-framing) |
| **2.8** | D10 | Design | Candidate transparency only after human approval | Decided | 2026-11-22 | baseline | [2-design.md](./2-design.md#28--candidate-transparency-timing) |
| **2.9** | — | Design | Living `docs/api.md` synced with `springdoc-openapi` | Decided | 2026-11-23 | prompter-change | [2-design.md](./2-design.md#29--living-docsapimd-synced-with-springdoc-openapi) |
| **3.1** | — | Team & Process | Single `openspec/changes/` plan tree + cold read-only review separation | Decided | 2026-11-23 | prompter-change | [3-team-process.md](./3-team-process.md#31--single-openspecchanges-plan-tree--cold-review-separation) |
| **3.2** | — | Team & Process | Canonical living coding standards (`.agents/rules/coding-standards.md`) | Decided | 2026-11-23 | prompter-change | [3-team-process.md](./3-team-process.md#32--canonical-living-coding-standards) |
| **3.3** | — | Team & Process | Mandatory decision recording on question rounds & prompter changes | Decided | 2026-11-23 | prompter-change | [3-team-process.md](./3-team-process.md#33--mandatory-decision-recording--code-pointers) |
| **1.8** | — | Architecture | Dual forge: GIT_FORGE=both with GitHub primary and GitLab secondary mirror | Decided | 2026-11-23 | prompter-change | [1-architecture.md](./1-architecture.md#18--dual-forge-git_forgeboth-with-github-primary-and-gitlab-secondary-mirror) |
| **3.4** | — | Team & Process | Unified PR/MR template and cross-tool scoped rule dispatch | Decided | 2026-11-23 | prompter-change | [3-team-process.md](./3-team-process.md#34--unified-prmr-template-and-cross-tool-scoped-rule-dispatch) |
<!-- DECISION_INDEX_END -->

---

## 2. Open Team Decisions (TBD)

These items remain open for Day-1 team alignment (resolve via `question-round` + `node scripts/record-decision.mjs ... --resolves O<k>`):

| ID | Question | Options / Constraints | Owner | Status |
|---|---|---|---|---|
| **O1** | **Frontend UI component library** | **PrimeVue** (rich data tables/sliders out of the box) vs. **shadcn-vue** (copy-paste Radix/Reka primitives on Tailwind CSS v4). | Frontend leads | Open |
| **O2** | **Interviewer LLM provider** *(evaluator resolved in 1.6 / D14)* | Evaluator uses **ki:connect NRW** (1.6). Decide whether conversational interviewer (Step 1) also uses ki:connect NRW or a separate EU-hosted streaming endpoint. | Backend / AI lead | Open |
| **O3** | **MVP skill taxonomy size** | Keep all 16 course sub-skills or trim to 6–8 core project-readiness skills for block-week velocity. | Whole team + Lecturer | Open |
| **O4** | **Team size & solver constraints** | Confirm lecturer's exact team size (default 5) and hard/soft constraints (e.g., >=1 strong backend/frontend per team). | Team Builder lead + Lecturer | Open |
| **O5** | **Formula expression editor in MVP** | Ship presets only in MVP or include the sandboxed SpEL expression editor. | Formula Engine lead | Open |
| **O6** | **Candidate result visibility timing** | Show results immediately after individual score approval, or only after final teams are published? | Whole team | Open |
| **O7** | **Demo transcript data retention** | Define retention / purge window for real or synthetic transcripts in the demo environment. | Whole team | Open |
