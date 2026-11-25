# Design: `nov24-reconciliation`

This change edits specs and docs only. The sections below fix the shapes that the later code changes build. `docs/architecture.md` holds the long-lived copy of §2–§6.

## 1. Decision → Artifact Map

| Decision | Topic | Artifacts |
|---|---|---|
| 4.1 | Feature list wins, canvas archived | `docs/archive/discovery-canvas-2026-11-22.md`, `docs/discovery-canvas.md` |
| 4.2 | Post-MVP and removed items | `docs/feature_backlog.md` |
| 4.3 | BFI-10 step | REQ-AC-14..16, REQ-TB-06..07, REQ-CR-06 |
| 4.4 (O7) | Real data, kept until deleted | REQ-IS-07, `AGENTS.md` §2 rule 8 |
| 2.10 (O6) | Threshold-gated release | REQ-SM-05..06, REQ-CR-01..02, `AGENTS.md` §2 rules 5–6, `openspec/config.yaml` |
| 2.11 | (question, skill) confidence | REQ-EP-03, REQ-SM-01 |
| 2.12 | Coverage over weighted questions | REQ-FE-03 |
| 2.13 | Attempt limit, latest counts | REQ-AC-11, REQ-SM-07 |
| 2.14 | Min/max time, no pause | REQ-AC-07..10, REQ-EP-06..07 |
| 2.15 | Full conversation log | REQ-AC-04, REQ-SM-08, REQ-CR-04 |
| 2.16 | Deterministic advice | REQ-CR-05, `docs/design_system.md` |
| 2.17 (O3), 2.18 (O4) | Professor form, no hard-coded values | REQ-IS-05..06, REQ-TB-05 |
| 2.19 (O5) | Presets only | REQ-FE-04 removed, REQ-FE-05 |
| 1.11 | Answers stream the reply, ledger | REQ-AC-04..06, `docs/api.md`, `docs/architecture.md` |
| 1.12 | Dual-agent isolation | REQ-AC-12..13, REQ-EP-05, `AGENTS.md` §2 |
| 1.13 (O2) | Interviewer on ki:connect NRW | `docs/architecture.md` |
| 1.14 (O1) | PrimeVue, 0–4 + hatched N/A | `docs/design_system.md`, `.agents/rules/frontend-vue.md` |
| 1.15 | Two roles | REQ-AA-01, `Role` in the enum table, `AGENTS.md`, `backend-java.md` |
| 1.16 | `auth` and `audit` slices, `studio` owns tags | REQ-IS-02, slice map |
| 1.17 | Invite-link sign-in | REQ-AA-03..05 |
| 3.12 | `docs/ai_logs/` | `docs/ai_logs/README.md`, `architecture_handover.md` |
| 3.13 | Java/Spring conventions, read order | `backend-java.md`, `AGENTS.md`, `CLAUDE.md` |
| 3.14 | Backlog format | `docs/feature_backlog.md` |

## 2. Conversation Ledger

One append-only table, `conversation_entry`, owned by the `assessment` slice:

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `attempt_id` | UUID FK → `assessment_attempt` | |
| `seq` | int | Order inside the attempt, unique with `attempt_id` |
| `author` | `interviewer \| candidate` | |
| `core_question_id` | UUID FK, nullable | Set for questions, follow-ups and answers |
| `origin` | `core \| follow_up \| invented`, nullable | Questions only |
| `content` | text | Message text. Empty for a timeout without text |
| `target_skills` | text[] | Questions only |
| `created_at` | timestamptz | Server time. `askedAt` for a question |
| `expires_at` | timestamptz, nullable | Questions only (REQ-AC-07) |
| `timed_out` | boolean | Answers only (REQ-AC-08) |
| `paste_suspected` | boolean | Answers only (REQ-AC-09) |

- **Immutability:** a Flyway trigger rejects every `UPDATE`. It rejects `DELETE` unless the transaction set `SET LOCAL nifty.allow_purge = 'on'`. Only the assessment deletion service (REQ-IS-07) sets it.
- **Export:** `assessment-transcript/v1` is a view built from the ledger. `answer.durationSec` = answer `created_at` − question `created_at`.
- **Grace period:** an application property (`nifty.assessment.answer-grace`, default 5 s). It covers network delay only, so it is not part of the professor form.

## 3. Interview Flow

```text
POST /sessions                → 201 + SSE: greeting, question 1 {expiresAt}
POST /sessions/{id}/answers   → SSE: follow-up or next question {expiresAt} | done
GET  /sessions/{id}           → state from the ledger (refresh)
POST /sessions/{id}/bfi10     → 204 (only WHERE BFI-10 is on, after the last core question)
```

The state machine (`assessment` slice, Java) holds: `IN_PROGRESS → BFI10 → COMPLETED`, or `IN_PROGRESS → COMPLETED` when BFI-10 is off. It enforces question order, the follow-up limit and the deadlines. An input that the state does not allow gets `409`. On every request, the service first closes any question whose `expiresAt` plus the grace period lies in the past (REQ-AC-08). `COMPLETED` publishes `AttemptCompletedEvent`. The evaluator listens `AFTER_COMMIT` (REQ-EP-05).

## 4. BFI-10 Items

Fixed in code (Rammstedt & John, 2007). Stem: "I see myself as someone who…"

| # | Item | Trait | Key |
|---|---|---|---|
| 1 | is reserved | Extraversion | R |
| 2 | is generally trusting | Agreeableness | |
| 3 | tends to be lazy | Conscientiousness | R |
| 4 | is relaxed, handles stress well | Neuroticism | R |
| 5 | has few artistic interests | Openness | R |
| 6 | is outgoing, sociable | Extraversion | |
| 7 | tends to find fault with others | Agreeableness | R |
| 8 | does a thorough job | Conscientiousness | |
| 9 | gets nervous easily | Neuroticism | |
| 10 | has an active imagination | Openness | |

`R`: score = 6 − response. Trait = mean of its 2 items (1.0–5.0). Answers and trait scores are stored in `bfi_response` (`assessment` slice). The `teams` slice reads trait scores through the `assessment` service.

## 5. Release States

`reviewStatus` literals: `pending`, `flagged`, `auto_released`, `approved`, `overridden`.

```text
pending ──(all gates pass, REQ-SM-05)──> auto_released ──(override)──> overridden
pending | flagged ──(professor)──> approved | overridden
```

An attempt is released when every score is `auto_released`, `approved` or `overridden`. Released scores feed the matrix, the cohort gaps and team building.

## 6. Slices and Reserved Migrations

| Slice | Owns | Reserved Flyway file |
|---|---|---|
| `auth` | professor accounts | `V2__auth_accounts.sql` |
| `audit` | `AuditLog` entity over `V1` `audit_log`, `AuditService` | — (uses `V1`) |
| `studio` | assessment configuration, versions, skills, tags | `V3__studio.sql` |
| `auth` | invite tokens (hash) | `V4__auth_invites.sql` |
| `formula` | formula versions (preset name, weights) | `V5__formula.sql` |
| `assessment` | attempts, ledger, BFI-10 responses | `V6__assessment.sql` |
| `evidence` | evidence items, (question, skill) scores, confidence | `V7__evidence.sql` |
| `matrix` | review status, overrides | `V8__matrix.sql` |
| `teams` | team proposals, placements | `V9__teams.sql` |
| `results` | none (reads other slices through services) | — |

`auth` has two files because the chain is account → configuration → invite. A change that needs a second migration uses the next free number above `V9` and claims it in the backlog first.

## 7. Doc Edit Rules

- New or changed prose follows ASD-STE100. Check it with `ste-lint.py`. EARS lines stay verbatim.
- Older decision entries stay as written. New text links the superseding decision.
- Each spec's `Primary View` and `Purpose` lines are edited directly in `openspec/specs/`, because a delta cannot change them.
