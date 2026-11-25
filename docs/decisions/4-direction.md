# Category 4 — Product & Direction Decisions (`4.x`)

Product scope shifts, lecturer feedback, MVP cuts, and prompter direction pivots. See [index.md](./index.md) for the full registry.

Use `scripts/record-decision.mjs --category 4 --trigger prompter-change ...` whenever product scope, demo priorities, or lecturer requirements change during the block week.

---

## 4.1 — Nov 24 feature list is the source of truth; discovery canvas archived
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** The Nov 24 feature list (Nov 23 team meeting) contradicts the discovery canvas, older decisions and the seed specs.
- **Decision:** Where documents disagree, docs/feature-list-2026-11-24.md wins, as refined by the decisions recorded on 2026-11-25. The old canvas moves to docs/archive/discovery-canvas-2026-11-22.md with an OUTDATED banner. docs/discovery-canvas.md is rewritten to the current product.
- **Alternatives Considered:** Keep the canvas as the product vision and patch the feature list into it; delete the old canvas.
- **Rationale / Justification:** The meeting is the newest team agreement. The archived copy keeps the discovery history readable, and the banner stops agents from following it.
- **Affected Files / Pointers:** docs/feature-list-2026-11-24.md, docs/discovery-canvas.md, docs/archive/discovery-canvas-2026-11-22.md

## 4.2 — Nov 24 open items: Post-MVP scope and removed ideas
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** The feature list leaves case-study matching and LLM-suggested Studio questions open, and removes AI detectors.
- **Decision:** Case-study/project matching and LLM-suggested core questions are Post-MVP. AI detectors are out of scope; the min/max time per question replaces them. The '(my call)' and '(to confirm)' items of the feature list are adopted as written.
- **Alternatives Considered:** Build both open items in MVP; keep the (my call) items open as O8+.
- **Rationale / Justification:** Block-week velocity. The adopted items are the team's own proposals and close the gaps in the release flow.
- **Affected Files / Pointers:** docs/feature_backlog.md, docs/feature-list-2026-11-24.md

## 4.3 — Optional BFI-10 personality test in the assessment
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** The feature list removed the personality-test slider. The user then asked for an optional standard personality test that feeds team building.
- **Decision:** The professor can turn on a BFI-10 step per assessment. It runs after the LLM questions: 10 fixed items, stem 'I see myself as someone who...', 5-point Likert scale (1 = Disagree strongly ... 5 = Agree strongly). Code scores 5 traits with the authors' key (Extraversion 1R, 6; Agreeableness 2, 7R; Conscientiousness 3R, 8; Neuroticism 4R, 9; Openness 5R, 10; R = 6 minus the response); each trait is the mean of its 2 items. Team building uses a soft constraint that keeps each team's average trait profile close to the cohort average; the professor sets its weight. Traits never change skill scores, fit, coverage or ranking. The professor sees trait scores in the team builder; a student sees only their own, after release, in neutral words.
- **Alternatives Considered:** No personality data (feature list); mix traits within each team; professor-only or solver-only visibility.
- **Rationale / Justification:** A validated, fixed instrument is scored deterministically and stays out of the skill evidence chain. Balancing avoids teams that cluster on one trait.
- **Affected Files / Pointers:** openspec/specs/assessment-chat/spec.md, openspec/specs/team-builder/spec.md, openspec/specs/candidate-results/spec.md

## 4.4 — Real data only; kept until the professor deletes it
- **Resolves:** O7
- **Date:** 2026-11-25
- **Triggered By:** question-round
- **Context:** O7 asked for a retention window for demo transcripts. The team works with real data that it creates itself, not synthetic seed data.
- **Decision:** No synthetic seed data. The system keeps transcripts, scores and BFI-10 answers until the professor deletes the assessment. Deletion removes all candidate data of that assessment and writes an audit_log entry.
- **Alternatives Considered:** Automatic purge a fixed time after the course; keep forever with no delete.
- **Rationale / Justification:** Real personal data needs a working delete path (GDPR). A manual delete is enough for the course scope.
- **Affected Files / Pointers:** openspec/specs/interviewer-studio/spec.md, docs/architecture.md

## 4.5 — No cost limit per interview
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** The canvas listed the LLM budget per interview as an open question.
- **Decision:** The system sets no cost or token limit per interview. The time limits and the follow-up limit per core question bound the interview length.
- **Alternatives Considered:** A token or cost budget per interview that ends the session.
- **Rationale / Justification:** The course uses ki:connect NRW at no cost to students, and the configured limits already bound the length.
- **Affected Files / Pointers:** docs/discovery-canvas.md
