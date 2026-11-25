# Category 2 — Design Decisions (`2.x`)

Domain model, scoring/evidence contracts, API documentation, and UX design decisions. See [index.md](./index.md) for the full registry.

---

## 2.1 — Inferred tags from answers
- **Legacy ID:** D1
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Free-form candidate answers mention technologies and concepts outside the pre-defined skill taxonomy.
- **Decision:** Evidence extractor suggests inferred tags with `status: pending`; professors approve, merge, or reject them into the controlled taxonomy.
- **Alternatives Considered:** Auto-accepting LLM-extracted tags directly into the taxonomy.
- **Rationale / Justification:** Prevents tag sprawl (e.g., "Docker" vs. "containers") and keeps cross-candidate calibration consistent.
- **Affected Files / Pointers:** `docs/architecture.md`, `openspec/specs/evidence-pipeline/spec.md`

## 2.2 — Multi-skill answers
- **Legacy ID:** D2
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** A single conversational answer frequently demonstrates competency across multiple skills simultaneously.
- **Decision:** Allow one Q/A entry to produce evidence for multiple skills, each with its own level and confidence; cap single-Q/A impact per skill in the default formula.
- **Alternatives Considered:** Restricting each Q/A pair to a single target skill.
- **Rationale / Justification:** Reflects realistic interviews without allowing one verbose answer to dominate all scores.
- **Affected Files / Pointers:** `openspec/specs/evidence-pipeline/spec.md`, `openspec/specs/formula-engine/spec.md`

## 2.3 — Question & skill weights
- **Legacy ID:** D3
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Professors need to weight both individual core questions and overall skill importance while keeping scores explainable.
- **Decision:** Support both question weights (`w_q`) and skill weights (`w_s`) and display every multiplication step inside the Evidence Drawer.
- **Alternatives Considered:** Skill weights only without per-question weighting.
- **Rationale / Justification:** Full numerical transparency for professors and candidates.
- **Affected Files / Pointers:** `openspec/specs/formula-engine/spec.md`, `openspec/specs/skills-matrix-review/spec.md`

## 2.4 — Invented questions
- **Legacy ID:** D4
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Adaptive probing helps clarify edge-case answers, but unstandardized questions can skew cohort comparisons.
- **Decision:** Make invented questions optional via an Interviewer Studio toggle; tag them `origin: invented` with lower default weight and optional exclusion from cross-candidate ranking.
- **Alternatives Considered:** Disallowing adaptive follow-ups completely; treating invented questions identically to core questions.
- **Rationale / Justification:** Preserves cohort comparability while allowing targeted adaptive probing.
- **Affected Files / Pointers:** `openspec/specs/interviewer-studio/spec.md`, `docs/architecture.md`

## 2.5 — Turn budget vs. skill count
- **Legacy ID:** D5
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Finite interview turn budgets mean not every skill can always be probed deeply.
- **Decision:** Core questions guarantee baseline coverage; any untested skill with zero verified evidence MUST be recorded as explicit `N/A` (never `0` or `1`) and excluded from fit calculations.
- **Alternatives Considered:** Scoring unasked skills as `0` or `1`.
- **Rationale / Justification:** Prevents penalizing candidates for unasked domains and separates `fit` from `coverage`.
- **Affected Files / Pointers:** `AGENTS.md`, `docs/architecture.md`, `openspec/specs/formula-engine/spec.md`

## 2.6 — Post-interview config changes
- **Legacy ID:** D6
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Professors may tweak weights/formulas or add new skills/questions after some candidates have already completed interviews.
- **Decision:** Weight and formula changes recompute deterministically immediately; adding skills or core questions creates a new config version and flags earlier candidates as incomplete.
- **Alternatives Considered:** Mutating existing config versions in place.
- **Rationale / Justification:** Protects evidence integrity and auditability across versioned configurations.
- **Affected Files / Pointers:** `openspec/specs/interviewer-studio/spec.md`, `openspec/specs/formula-engine/spec.md`

## 2.7 — Ranking View UX framing
- **Legacy ID:** D9
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** EU AI Act Annex III and educational fairness discourage opaque, single-number student leaderboards.
- **Decision:** Keep the internal module name "Ranking View" for continuity, while emphasizing skill-spec fit, coverage, and team balance in the UI instead of a global leaderboard.
- **Alternatives Considered:** Single-score ranked leaderboard table.
- **Rationale / Justification:** Reduces EU AI Act / discrimination liability and better serves balanced team formation.
- **Affected Files / Pointers:** `openspec/specs/skills-matrix-review/spec.md`, `frontend/src/views/`

## 2.8 — Candidate transparency timing
- **Legacy ID:** D10
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Candidates deserve full explainability of how their assessment was evaluated without gaming live prompts.
- **Decision:** Reveal formula, weights, and verified evidence quotes to the candidate **only after human approval** (with a mocked dispute flow in MVP); never display scores or weights during an active interview.
- **Alternatives Considered:** Showing live running scores during chat; hiding formulas permanently.
- **Rationale / Justification:** Prevents mid-interview gaming while providing full post-approval GDPR/EU AI Act explainability.
- **Affected Files / Pointers:** `AGENTS.md`, `openspec/specs/assessment-chat/spec.md`, `openspec/specs/candidate-results/spec.md`

## 2.9 — Living `docs/api.md` synced with `springdoc-openapi`
- **Date:** 2026-11-23
- **Triggered By:** prompter-change
- **Context:** Frontend/backend teammates, AI agents, and course graders need fast, readable API documentation without booting the backend first.
- **Decision:** Maintain a concise living `docs/api.md` (endpoint table, auth roles, DTO shapes, error envelope, SSE contracts) updated in every PR that modifies backend controllers, complemented by live `springdoc-openapi` (`/v3/api-docs`, `/swagger-ui.html`).
- **Alternatives Considered:** Relying solely on runtime Swagger UI; committing large auto-generated OpenAPI dumps without human summary.
- **Rationale / Justification:** Gives humans and agents immediate offline contract visibility while keeping runtime OpenAPI schemas authoritative for TypeScript codegen.
- **Affected Files / Pointers:** `docs/api.md`, `.agents/rules/coding-standards.md`, `.claude/agents/reviewer.md`

## 2.10 — Threshold-gated release of results (supersedes 2.8 timing)
- **Resolves:** O6
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** The feature list replaces 'a human approves every score' with professor-set trust thresholds.
- **Decision:** WHEN every question meets its confidence threshold, coverage meets the minimum, and no answer is flagged, the result is released to the student automatically (reviewStatus auto_released). Otherwise the assessment goes to the professor's review queue; the student sees results only after approval. Configuration approval and team publishing stay mandatory human gates. Students see results as soon as they are released, not after teams are published.
- **Alternatives Considered:** Keep human approval of every score (2.8); release only after teams are published.
- **Rationale / Justification:** Professors spend review time where the evidence is weak. Human oversight (EU AI Act Art. 14) stays: a human sets every threshold, reviews everything below them, and can override any released score with a reason in audit_log.
- **Affected Files / Pointers:** AGENTS.md, openspec/config.yaml, openspec/specs/skills-matrix-review/spec.md, openspec/specs/candidate-results/spec.md

## 2.11 — Confidence per (question, skill) with a per-question threshold
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** The feature list sets a confidence threshold per question. Decision 2.2 lets one answer give evidence for several skills.
- **Decision:** Code calculates confidence for each (question, skill) score from measurable signals. The professor sets one minimum confidence per question. A question passes only when every skill it scores meets that threshold. This replaces the per-skill lower/upper bounds of UX-04.
- **Alternatives Considered:** One confidence per question; keep the per-skill two-bound model.
- **Rationale / Justification:** Keeps Decision 2.2 and the deterministic-confidence invariant, and matches the feature list. A weak skill cannot hide behind a strong one.
- **Affected Files / Pointers:** openspec/specs/evidence-pipeline/spec.md, openspec/specs/skills-matrix-review/spec.md

## 2.12 — Coverage over weighted questions with a professor-set minimum
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** Canvas section 11.2 and REQ-FE-03 compute coverage over weighted skills. The feature list uses weighted questions.
- **Decision:** coverage = sum of weights of answered, scored questions / sum of weights of all core questions. A timed-out question counts as unanswered (N/A). The professor sets the minimum coverage per assessment (for example 70%). Below the minimum, the assessment goes to review.
- **Alternatives Considered:** Weighted skills (canvas 11.2).
- **Rationale / Justification:** Questions are what the professor configures and what can time out, so the gate is easy to explain.
- **Affected Files / Pointers:** openspec/specs/formula-engine/spec.md, openspec/specs/interviewer-studio/spec.md

## 2.13 — Attempt limit; the latest attempt counts
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** The feature list adds an attempt limit and leaves open which attempt counts.
- **Decision:** The professor sets the maximum number of attempts per assessment. The latest completed attempt counts. Older attempts stay in the ledger for audit but leave the matrix, cohort statistics and team building.
- **Alternatives Considered:** Best attempt counts; professor chooses per assessment.
- **Rationale / Justification:** A retake is a real second try, not a lottery, and one rule is simpler to build and explain.
- **Affected Files / Pointers:** openspec/specs/interviewer-studio/spec.md, openspec/specs/assessment-chat/spec.md

## 2.14 — Min/max time per question; no pause and resume
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** The feature list replaces AI detectors with a minimum and maximum time per question and removes pause/resume from UX-09.
- **Decision:** The professor sets a minimum and maximum time for each core question. The server measures time from its own timestamps in the ledger. An answer under the minimum is flagged paste_suspected for review. WHEN the maximum passes, the question is N/A (unanswered, not wrong). The chat shows a countdown and the questions-left indicator. Pause and resume is removed. Follow-up questions use the time limits of their core question. A follow-up that times out drops only that follow-up entry. The core question keeps its other entries.
- **Alternatives Considered:** AI text detectors; client-side timers; keep pause and resume.
- **Rationale / Justification:** Server timestamps cannot be faked by the client, and timing signals are deterministic, unlike AI detectors.
- **Affected Files / Pointers:** openspec/specs/assessment-chat/spec.md, openspec/specs/evidence-pipeline/spec.md

## 2.15 — Full conversation log visible to the student after release
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** The feature list asks for a full, timestamped, unchangeable log of the interview.
- **Decision:** Every student answer and every AI reply is recorded with a server timestamp and cannot change afterwards. The professor sees the full log. The student sees their own full interview after release.
- **Alternatives Considered:** Show the student only the verified quotes.
- **Rationale / Justification:** Full transparency of the evidence base, and the same ledger feeds the evaluator.
- **Affected Files / Pointers:** openspec/specs/assessment-chat/spec.md, openspec/specs/candidate-results/spec.md

## 2.16 — Results page advice is deterministic first
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** The feature list adds friendly advice to UX-10: strongest skills and where to improve.
- **Decision:** Code selects the strongest skills and the improvement areas from approved or released scores. An optional AI sentence may phrase them; it is labelled as AI, uses only those computed values, and is encouraging but specific (at most 2 sentences).
- **Alternatives Considered:** Let the LLM choose strengths and weaknesses; no advice.
- **Rationale / Justification:** Keeps the 'no LLM math' invariant while making the page friendlier.
- **Affected Files / Pointers:** openspec/specs/candidate-results/spec.md, docs/design_system.md

## 2.17 — Assessment configuration is a professor form, nothing hard-coded
- **Resolves:** O3
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** O3 (taxonomy size) and O4 (team size and constraints) assumed fixed values. The professor fills a form when creating an assessment.
- **Decision:** The professor sets per assessment: skills and their weights, core questions with weights, target skills, confidence threshold and min/max time, minimum coverage, maximum attempts, BFI-10 on/off, team size, and the soft-constraint weights. The code holds no fixed taxonomy size or team size. Resolves O3 and O4.
- **Alternatives Considered:** Fixed 6-8 core skills; fixed team size 5.
- **Rationale / Justification:** Each course is different; a form is easy to change without code changes.
- **Affected Files / Pointers:** openspec/specs/interviewer-studio/spec.md, openspec/specs/team-builder/spec.md

## 2.18 — Team size and solver constraints come from the assessment form
- **Resolves:** O4
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** O4 asked for the lecturer's team size and constraints.
- **Decision:** Team size and soft-constraint weights come from the assessment form (Decision 2.17). Hard constraints: every candidate is in exactly one team; team sizes differ by at most one. Soft constraints: balanced average fit, at least one strong member per key skill, and the BFI-10 balance when it is on.
- **Alternatives Considered:** Fixed team size 5 in code.
- **Rationale / Justification:** Same as Decision 2.17.
- **Affected Files / Pointers:** openspec/specs/team-builder/spec.md

## 2.19 — Formula presets only in MVP
- **Resolves:** O5
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** O5 asked whether the sandboxed SpEL expression editor is in MVP.
- **Decision:** MVP ships formula presets only. The SpEL expression editor is Post-MVP.
- **Alternatives Considered:** Ship the sandboxed SpEL editor in MVP.
- **Rationale / Justification:** Presets cover the demo and avoid sandbox security work in block week.
- **Affected Files / Pointers:** openspec/specs/formula-engine/spec.md, docs/feature_backlog.md

## 2.20 — Answers submitted at timeout are evaluated
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** Decisions 2.12 and 2.14 made a timed-out question N/A. The browser auto-submits the typed text at zero, and no static check can tell a partial answer from a complete one.
- **Decision:** An answer that arrives after expiresAt but within the grace period is stored with timedOut = true and evaluated like any other answer. A question is N/A only when no answer text arrives: the answer is empty, arrives after the grace period, or never arrives. Coverage counts only such questions as unanswered. timedOut stays a visible marker in the conversation log; it does not flag the score. Supersedes the timeout parts of 2.12 and 2.14.
- **Alternatives Considered:** Treat every timed-out answer as N/A (feature list); flag timed-out answers for review.
- **Rationale / Justification:** Partial text can hold real evidence, and quote verification plus confidence already handle weak answers.
- **Affected Files / Pointers:** openspec/specs/assessment-chat/spec.md, openspec/specs/evidence-pipeline/spec.md, openspec/specs/formula-engine/spec.md

## 2.21 — One-shot immutable assessment creation with browser localStorage draft
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** AssessmentCreationService replaces backend draft/version CRUD with a browser localStorage form submitted once via Idempotency-Key and locked by DB triggers.
- **Decision:** Professors draft in browser localStorage and submit once via POST /api/v1/ranking/studio/configs with Idempotency-Key, creating an immutable PUBLISHED v1 config protected by PostgreSQL mutation triggers (allowing DELETE only under SET LOCAL nifty.allow_purge = 'on'). Keep max_attempts (default 1), invented_questions (default false), and DELETE /api/v1/ranking/studio/configs/{id} (Decision 4.4). Defer backend drafts, config versioning, and inferredTags UI to Post-MVP. Supersedes 2.6 (post-interview config versions) and the MVP scope of 2.1 (inferred tags).
- **Alternatives Considered:** Full backend draft and multi-version config CRUD in MVP; drop max_attempts and DELETE
- **Rationale / Justification:** Cuts studio complexity while preserving immutability, multi-attempt support, and GDPR deletion.
- **Affected Files / Pointers:** openspec/specs/interviewer-studio/spec.md, docs/architecture.md, docs/api.md

## 2.22 — Question-weighted scoring without skill weights; D2 cap and 1.0 follow-up inheritance
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** studio removed SKILL.weight while evaluation and architecture.md still assumed w_s and 0.70 follow-up multipliers.
- **Decision:** Skills carry no grading weight (no SKILL.weight column). Only core questions have weight > 0, and follow-ups inherit 1.0 * coreQuestion.weight. Multi-skill answers give each targeted skill the full question weight capped per entry at max_entry_skill_weight_share = 0.50 (Decision 2.2). fit is the unweighted mean across SCORED skills; coverage remains weighted over answered core questions (Decision 2.12). Ratios and thresholds are 0..1 decimals in DB/API (UI displays percent). An invented-question entry keeps the lower weight of 2.4: 0.5 * the weight of the active core question, and it never counts toward coverage. Supersedes the skill weights w_s of 2.3 and 2.17.
- **Alternatives Considered:** Keep both skill and question weights; apply 0.70 follow-up multiplier
- **Rationale / Justification:** One clear weighting axis configured by the professor, zero double-weighting confusion, aligned across studio and evaluation.
- **Affected Files / Pointers:** openspec/specs/interviewer-studio/spec.md, openspec/specs/formula-engine/spec.md, docs/architecture.md

## 2.23 — Grouping config fields and SOFT_SKILLS-only mode
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** teams and assessment need grouping_mode, soft_preset, combined_hard_weight, alpha, and SOFT_SKILLS-only routing while studio rejected empty question lists.
- **Decision:** Store team_size, grouping_mode (HARD_SKILLS | SOFT_SKILLS | COMBINED), soft_preset (ENGINEERING_DEFAULT | EQUAL_WEIGHTS | CUSTOM), combined_hard_weight, alpha, and constraint_weights on assessment_config (deriving numTeams = ceil(N / teamSize) with optional run-time override in Team Builder). Allow 0 skills and 0 core questions ONLY when grouping_mode == SOFT_SKILLS and bfi10_enabled == true; otherwise require >=1 skill, >=1 core question, and no unused skills.
- **Alternatives Considered:** Forbid SOFT_SKILLS-only mode in MVP
- **Rationale / Justification:** Aligns studio, assessment, and teams on one schema while supporting BFI-10-only workshops.
- **Affected Files / Pointers:** openspec/specs/interviewer-studio/spec.md, openspec/specs/assessment-chat/spec.md, openspec/specs/team-builder/spec.md

## 2.24 — Deterministic prompt-injection and character-velocity anomaly flags
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** Team rules require prompt-injection resistance and deterministic cheating anomaly flagging without LLM-guessed probabilities.
- **Decision:** context owns deterministic PromptSanitizer and InjectionDetector, returning injectionSuspected + matchedRuleIds and logging security events via AuditService with zero raw PII. assessment persists injection_suspected alongside server-timed paste_suspected (including a character-velocity check). evaluation forces reviewStatus = flagged on any score supported by an entry with pasteSuspected or injectionSuspected.
- **Alternatives Considered:** Prompt-only guardrails without deterministic detection or flagging
- **Rationale / Justification:** Defense-in-depth keeps every suspected cheat or injection routed to human professor review with zero LLM math.
- **Affected Files / Pointers:** openspec/specs/context-creation/spec.md, openspec/specs/assessment-chat/spec.md, openspec/specs/evidence-pipeline/spec.md

## 2.25 — Evaluator returns machine-parsable output; code computes every aggregate and ranking
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** The cold review found that no spec still said that LLMs never compute rankings or team-balance numbers. The user confirmed the split between the evaluator LLM and code.
- **Decision:** For each (entry, skill) the evaluator LLM returns one JSON object in a fixed schema: level (0..4), verbatimQuote, rationale and an optional improvement suggestion. A deterministic parser validates the object against the schema and the rubric range and rejects anything else. The LLM never returns totals, fit, coverage, confidence, rankings or team-balance numbers. Java and SQL functions compute those from all the validated levels.
- **Alternatives Considered:** Free-text evaluator output parsed with heuristics; letting the LLM rank candidates
- **Rationale / Justification:** A fixed schema makes the LLM output testable and keeps every number that affects a student deterministic (AGENTS.md §2.1).
- **Affected Files / Pointers:** openspec/specs/evidence-pipeline/spec.md, openspec/specs/context-creation/spec.md, docs/architecture.md
