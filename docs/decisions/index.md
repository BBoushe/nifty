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
| **1.9** | — | Architecture | CI parity: GitHub CI gates the same jobs as GitLab CI | Decided | 2026-11-24 | prompter-change | [1-architecture.md](./1-architecture.md#19--ci-parity-github-ci-gates-the-same-jobs-as-gitlab-ci) |
| **1.10** | — | Architecture | Backend package-by-feature with ArchUnit-enforced boundaries | Decided | 2026-11-24 | prompter-change | [1-architecture.md](./1-architecture.md#110--backend-package-by-feature-with-archunit-enforced-boundaries) |
| **3.5** | — | Team & Process | Adopt ASD-STE100 for specs and docs; reject Caveman | Decided | 2026-11-24 | prompter-change | [3-team-process.md](./3-team-process.md#35--adopt-asd-ste100-for-specs-and-docs-reject-caveman) |
| **3.6** | — | Team & Process | AI context docs: feature history, feature backlog and ai_docs | Decided | 2026-11-24 | prompter-change | [3-team-process.md](./3-team-process.md#36--ai-context-docs-feature-history-feature-backlog-and-ai_docs) |
| **3.7** | — | Team & Process | Superpowers loops wired into agent roles | Decided | 2026-11-24 | prompter-change | [3-team-process.md](./3-team-process.md#37--superpowers-loops-wired-into-agent-roles) |
| **3.8** | — | Team & Process | Completed OpenSpec changes must be archived | Decided | 2026-11-24 | prompter-change | [3-team-process.md](./3-team-process.md#38--completed-openspec-changes-must-be-archived) |
| **3.9** | — | Team & Process | Parallel, resumable delivery by blast radius | Decided | 2026-11-24 | prompter-change | [3-team-process.md](./3-team-process.md#39--parallel-resumable-delivery-by-blast-radius) |
| **3.10** | — | Team & Process | All changes reach dev through a pull request | Decided | 2026-11-24 | prompter-change | [3-team-process.md](./3-team-process.md#310--all-changes-reach-dev-through-a-pull-request) |
| **3.11** | — | Team & Process | Orchestrated multi-agent delivery from one session | Decided | 2026-11-25 | prompter-change | [3-team-process.md](./3-team-process.md#311--orchestrated-multi-agent-delivery-from-one-session) |
| **4.1** | — | Direction | Nov 24 feature list is the source of truth; discovery canvas archived | Decided | 2026-11-25 | prompter-change | [4-direction.md](./4-direction.md#41--nov-24-feature-list-is-the-source-of-truth-discovery-canvas-archived) |
| **4.2** | — | Direction | Nov 24 open items: Post-MVP scope and removed ideas | Decided | 2026-11-25 | question-round | [4-direction.md](./4-direction.md#42--nov-24-open-items-post-mvp-scope-and-removed-ideas) |
| **4.3** | — | Direction | Optional BFI-10 personality test in the assessment | Decided | 2026-11-25 | question-round | [4-direction.md](./4-direction.md#43--optional-bfi-10-personality-test-in-the-assessment) |
| **4.4** | — | Direction | Real data only; kept until the professor deletes it | Decided | 2026-11-25 | question-round | [4-direction.md](./4-direction.md#44--real-data-only-kept-until-the-professor-deletes-it) |
| **2.10** | — | Design | Threshold-gated release of results (supersedes 2.8 timing) | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#210--threshold-gated-release-of-results-supersedes-28-timing) |
| **2.11** | — | Design | Confidence per (question, skill) with a per-question threshold | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#211--confidence-per-question-skill-with-a-per-question-threshold) |
| **2.12** | — | Design | Coverage over weighted questions with a professor-set minimum | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#212--coverage-over-weighted-questions-with-a-professor-set-minimum) |
| **2.13** | — | Design | Attempt limit; the latest attempt counts | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#213--attempt-limit-the-latest-attempt-counts) |
| **2.14** | — | Design | Min/max time per question; no pause and resume | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#214--minmax-time-per-question-no-pause-and-resume) |
| **2.15** | — | Design | Full conversation log visible to the student after release | Decided | 2026-11-25 | prompter-change | [2-design.md](./2-design.md#215--full-conversation-log-visible-to-the-student-after-release) |
| **2.16** | — | Design | Results page advice is deterministic first | Decided | 2026-11-25 | prompter-change | [2-design.md](./2-design.md#216--results-page-advice-is-deterministic-first) |
| **2.17** | — | Design | Assessment configuration is a professor form, nothing hard-coded | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#217--assessment-configuration-is-a-professor-form-nothing-hard-coded) |
| **2.18** | — | Design | Team size and solver constraints come from the assessment form | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#218--team-size-and-solver-constraints-come-from-the-assessment-form) |
| **2.19** | — | Design | Formula presets only in MVP | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#219--formula-presets-only-in-mvp) |
| **1.11** | — | Architecture | Answers stream the interviewer reply; append-only ledger | Decided | 2026-11-25 | prompter-change | [1-architecture.md](./1-architecture.md#111--answers-stream-the-interviewer-reply-append-only-ledger) |
| **1.12** | — | Architecture | Dual-agent rubric isolation | Decided | 2026-11-25 | prompter-change | [1-architecture.md](./1-architecture.md#112--dual-agent-rubric-isolation) |
| **1.13** | — | Architecture | Interviewer uses ki:connect NRW | Decided | 2026-11-25 | question-round | [1-architecture.md](./1-architecture.md#113--interviewer-uses-kiconnect-nrw) |
| **1.14** | — | Architecture | PrimeVue on Tailwind v4; design system uses 0-4 and hatched N/A | Decided | 2026-11-25 | question-round | [1-architecture.md](./1-architecture.md#114--primevue-on-tailwind-v4-design-system-uses-0-4-and-hatched-na) |
| **1.15** | — | Architecture | Two roles (PROFESSOR, CANDIDATE), easy to extend | Decided | 2026-11-25 | prompter-change | [1-architecture.md](./1-architecture.md#115--two-roles-professor-candidate-easy-to-extend) |
| **1.16** | — | Architecture | Backend slice map: auth and audit slices; studio owns inferred tags | Decided | 2026-11-25 | prompter-change | [1-architecture.md](./1-architecture.md#116--backend-slice-map-auth-and-audit-slices-studio-owns-inferred-tags) |
| **3.12** | — | Team & Process | docs/ai_logs/ prompt-as-code audit trail | Decided | 2026-11-25 | prompter-change | [3-team-process.md](./3-team-process.md#312--docsai_logs-prompt-as-code-audit-trail) |
| **3.13** | — | Team & Process | Java/Spring conventions and context read order from PDF 4 | Decided | 2026-11-25 | prompter-change | [3-team-process.md](./3-team-process.md#313--javaspring-conventions-and-context-read-order-from-pdf-4) |
| **3.14** | — | Team & Process | Backlog of atomic TDD stories with reserved migrations | Decided | 2026-11-25 | prompter-change | [3-team-process.md](./3-team-process.md#314--backlog-of-atomic-tdd-stories-with-reserved-migrations) |
| **1.17** | — | Architecture | Students join by invite link only | Decided | 2026-11-25 | question-round | [1-architecture.md](./1-architecture.md#117--students-join-by-invite-link-only) |
| **2.20** | — | Design | Answers submitted at timeout are evaluated | Decided | 2026-11-25 | prompter-change | [2-design.md](./2-design.md#220--answers-submitted-at-timeout-are-evaluated) |
| **4.5** | — | Direction | No cost limit per interview | Decided | 2026-11-25 | prompter-change | [4-direction.md](./4-direction.md#45--no-cost-limit-per-interview) |
| **1.18** | — | Architecture | Unified 7-slice backend package map (studio, context, assessment, evaluation, teams + auth, audit) | Decided | 2026-11-25 | question-round | [1-architecture.md](./1-architecture.md#118--unified-7-slice-backend-package-map-studio-context-assessment-evaluation-teams--auth-audit) |
| **1.19** | — | Architecture | Unified inter-service contracts (UUID IDs, AttemptCompletedEvent, ranking/studio routes, 400 ProblemDetail) | Decided | 2026-11-25 | question-round | [1-architecture.md](./1-architecture.md#119--unified-inter-service-contracts-uuid-ids-attemptcompletedevent-rankingstudio-routes-400-problemdetail) |
| **1.20** | — | Architecture | Seeded Java Simulated Annealing team optimizer (replaces Timefold in MVP) | Decided | 2026-11-25 | question-round | [1-architecture.md](./1-architecture.md#120--seeded-java-simulated-annealing-team-optimizer-replaces-timefold-in-mvp) |
| **2.21** | — | Design | One-shot immutable assessment creation with browser localStorage draft | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#221--one-shot-immutable-assessment-creation-with-browser-localstorage-draft) |
| **2.22** | — | Design | Question-weighted scoring without skill weights; D2 cap and 1.0 follow-up inheritance | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#222--question-weighted-scoring-without-skill-weights-d2-cap-and-10-follow-up-inheritance) |
| **2.23** | — | Design | Grouping config fields and SOFT_SKILLS-only mode | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#223--grouping-config-fields-and-soft_skills-only-mode) |
| **2.24** | — | Design | Deterministic prompt-injection and character-velocity anomaly flags | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#224--deterministic-prompt-injection-and-character-velocity-anomaly-flags) |
| **1.21** | — | Architecture | Context builder keeps separate per-attempt contexts for interviewer and evaluator | Decided | 2026-11-25 | question-round | [1-architecture.md](./1-architecture.md#121--context-builder-keeps-separate-per-attempt-contexts-for-interviewer-and-evaluator) |
| **2.25** | — | Design | Evaluator returns machine-parsable output; code computes every aggregate and ranking | Decided | 2026-11-25 | question-round | [2-design.md](./2-design.md#225--evaluator-returns-machine-parsable-output-code-computes-every-aggregate-and-ranking) |
| **1.22** | — | Architecture | All Spring tests run on PostgreSQL Testcontainers from Wave 0 | Decided | 2026-11-25 | team | [1-architecture.md](./1-architecture.md#122--all-spring-tests-run-on-postgresql-testcontainers-from-wave-0) |
<!-- DECISION_INDEX_END -->

---

## 2. Open Team Decisions (TBD)

O1–O7 are resolved (2026-11-25). Add a new open item here when one comes up, and resolve it via `question-round` + `node scripts/record-decision.mjs ... --resolves O<k>`:

| ID | Question | Options / Constraints | Owner | Status |
|---|---|---|---|---|
| **O1** | **Frontend UI component library** | **PrimeVue** (rich data tables/sliders out of the box) vs. **shadcn-vue** (copy-paste Radix/Reka primitives on Tailwind CSS v4). | Frontend leads | Resolved -> **1.14** |
| **O2** | **Interviewer LLM provider** *(evaluator resolved in 1.6 / D14)* | Evaluator uses **ki:connect NRW** (1.6). Decide whether conversational interviewer (Step 1) also uses ki:connect NRW or a separate EU-hosted streaming endpoint. | Backend / AI lead | Resolved -> **1.13** |
| **O3** | **MVP skill taxonomy size** | Keep all 16 course sub-skills or trim to 6–8 core project-readiness skills for block-week velocity. | Whole team + Lecturer | Resolved -> **2.17** |
| **O4** | **Team size & solver constraints** | Confirm lecturer's exact team size (default 5) and hard/soft constraints (e.g., >=1 strong backend/frontend per team). | Team Builder lead + Lecturer | Resolved -> **2.18** |
| **O5** | **Formula expression editor in MVP** | Ship presets only in MVP or include the sandboxed SpEL expression editor. | Formula Engine lead | Resolved -> **2.19** |
| **O6** | **Candidate result visibility timing** | Show results immediately after individual score approval, or only after final teams are published? | Whole team | Resolved -> **2.10** |
| **O7** | **Demo transcript data retention** | Define retention / purge window for real or synthetic transcripts in the demo environment. | Whole team | Resolved -> **4.4** |
