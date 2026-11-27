# Design: MVP Hardening (`mvp-hardening`)

Decisions: 2.31 (soft-only auto-release), 2.32 (remove the invented-questions toggle), 2.33 (expire and evaluate), 2.34 (re-invite after revoke). Existing: 1.12, 1.18, 1.21, 2.10, 2.29, 2.30.

## 1. Professor review in modals (frontend)

**Evidence modal.** Rename `views/ranking/EvidenceDrawer.vue` to `EvidenceDialog.vue`. Wrap its content in a PrimeVue `Dialog`:

- `modal`, `maximizable`, `dismissableMask`, `:style="{ width: 'min(1100px, 95vw)' }"`, `:breakpoints="{ '640px': '100vw' }"`.
- The `Dialog` handles Esc, the focus trap and focus return. Do not write them again.
- Header: candidate name (`candidateRef`), attempt number and the release-status tag. Footer: Close, Export JSON, View transcript, Release.
- `RankingView` binds `v-model:visible` to `selectedAttemptId !== null`. The matrix stays in place behind the mask.
- The inline "Full Conversation Ledger" section moves out of this file into the Transcript dialog.

**Transcript dialog.** Add `frontend/src/components/TranscriptDialog.vue`, shared by the professor and the candidate. It follows Rule 6 of the coding standards (reuse, no two copies).

```ts
// frontend/src/components/transcript.ts
export interface TranscriptTurn {
  id: string                       // entryId
  turn: number
  origin: 'core' | 'follow_up' | string
  question: string
  askedAt: string                  // ISO
  answer: string | null
  submittedAt: string | null
  durationSec: number | null
  markers: { timedOut: boolean; pasteSuspected: boolean; injectionSuspected: boolean }
}
export function fromTranscriptExport(e: TranscriptExportEntry): TranscriptTurn
export function fromCandidateTurn(t: CandidateConversationTurn): TranscriptTurn
export function formatDuration(sec: number | null): string   // "48 s", "1 min 12 s", "—"
```

Props: `visible`, `title`, `subtitle?`, `turns: TranscriptTurn[]`, `showMarkers: boolean`, `focusEntryId?: string`, `highlight?: Record<string, string[]>` (entryId → verified quotes). Emits `update:visible`. An `actions` slot holds Export JSON.

Layout (chat reading pattern):

- **Summary bar:** questions asked, answered, timed out, and flagged entries (flags only when `showMarkers`). The total time is the sum of `durationSec`.
- **Interviewer bubbles** on the left: a neutral surface and a small label ("Core question 2" / "Follow-up"), with the time asked as `HH:mm`.
- **Candidate bubbles** on the right: an accent tint and `whitespace-pre-wrap`. The meta line reads "answered in 1 min 12 s · 10:04". A missing answer shows a muted italic "No answer" bubble.
- **Markers:** small chips under the candidate bubble ("Timed out", "Paste suspected", "Injection suspected"), with `showMarkers` only. The candidate never sees them.
- **Highlights:** verified quotes in `highlight` show as `<mark>` inside the answer text. Use plain string search with no HTML injection: split the text, never use `v-html`.
- **Focus:** `focusEntryId` scrolls that turn into view on open and outlines it.
- Bubble text is at most `65ch` wide. Both the scroll container and each turn have an `aria-label`. Times use `Intl.DateTimeFormat` with the browser locale.

**Callers:**

- **Evidence modal:** a "View transcript" button opens the dialog. Each evidence item has "Show in transcript", which sets `focusEntryId` and `highlight`.
- **ResultsView (candidate):** after release, the inline conversation list becomes an "Open conversation" button and dialog with `showMarkers = false`.

## 2. Frontend fixes

- **Polling:** `SkillsMatrixPanel`, `EvidenceDialog` and `ResultsView` use `refetchInterval: (q) => anyUnfinished(q.state.data) ? 5000 : false`. "Unfinished" means `evaluationStatus` is `QUEUED` or `RUNNING`, or for the candidate, `released === false`. A small "Refresh" button calls `refetch()`.
- **Release button:** enabled only when `evaluationStatus === 'COMPLETED' && flaggedCount === 0 && !isAlreadyReleased`. A tooltip names the reason when it is disabled. This needs `evaluationStatus` in `AttemptEvaluationsResponse` (backend, §3).
- **Route sync:** `RankingView` watches `route.params.configId` → `selectedConfigId`, and `selectedConfigId` → `router.replace({ name: 'ranking-config', params })`. Use the route name that the current routes file already has.
- **Names:** show `candidateRef` wherever `inviteId.slice(0, 8)` appears: the matrix table, heatmap labels, formula preview and modal header.
- **Retake:** `AssessmentChatView` reads the session. IF the stored attempt is `COMPLETED` THEN it clears the `sessionStorage` key. WHEN `attemptsLeft > 0` it shows "Start new attempt"; WHEN `attemptsLeft === 0` it shows "No attempts left" and no Start button. If the session API does not return `attemptsLeft`, add it to `SessionStateResponse` (backend, §4).
- **404:** `{ path: '/:pathMatch(.*)*', component: NotFoundView }` in `views/auth/routes.ts`, with a link to the role start page.
- **Teams link:** RankingView header gets "Build teams" → `/ranking/teams/:configId` for the selected assessment.
- **Invites scroll:** `AssessmentDetailView` scrolls `#invites` into view `onMounted` when the route name is `studio-invites`.
- **Cache key:** both places use `['studio', 'assessments']`.
- **Backend down:** `App.vue` shows a full-width `Message` "Server not reachable. Retry" when `fetchMe()` fails with a network error (not `401`). Retry calls `fetchMe()` again.
- **Sign-out failure:** `App.vue` catches the `logout()` error and shows "Sign-out failed. Try again." It does not navigate.
- Delete `frontend/src/api/schema.d.ts` (no imports, checked with `grep`).

## 3. Evaluation (backend)

- **Judge JSON:** `TwoRunResponseJudge.parseStrict` first strips a leading Markdown code fence (` ```json ` or ` ``` `) and its closing fence. Then it takes the text from the first `{` to the matching last `}`. Schema validation stays strict. The judge call sets `OpenAiChatOptions.temperature(0.0)` per call. Do not set a global temperature: the interviewer keeps its default.
- **Final-try crash:** `EvaluationRetryScheduler` marks a stale `RUNNING` run that has used the maximum tries as `FAILED` with reason `STALE_FINAL_TRY`.
- **Re-run:** `POST /api/v1/ranking/attempts/{attemptId}/evaluation/retry` (`PROFESSOR`). It is allowed only for a `FAILED` run and answers `409` otherwise, or `404` for an attempt the professor does not own. It sets tries to 0 and status to `QUEUED`, and writes audit `EVALUATION_RETRIED`. The Evidence modal shows "Re-run evaluation" for `FAILED`. Sync `docs/api.md`.
- **Lost events:** the retry scheduler also asks `AssessmentQueryService.completedAttemptIdsWithoutEvaluation(configIds?)`, or an equivalent narrow method that the assessment slice owns. It creates the missing runs through the existing idempotent create (REQ-EP-09). Evaluation does not read assessment tables.
- **Snapshot weights:** the first scoring creates formula version 1 from the configuration snapshot. Every scoring, the first one too, reads weights from the formula version row and never from the live Studio view.
- **Lock order:** every write for one attempt locks the summary row first (`findByAttemptIdForUpdate`). This covers approve, override, release and the `AssessmentDeletedEvent` cleanup. One order means no deadlock.
- **Soft-only auto-release (Decision 2.31):** `ReleaseGateEvaluator` gets `hardSkillCount` and `softProfileComplete`. WHEN `hardSkillCount == 0 && softProfileComplete`, the result is `AUTO_RELEASED`. The fail-closed branch stays for every other empty case.
- **Names and status:** matrix rows and `AttemptEvaluationsResponse` gain `candidateRef`, read through the auth slice's public `InviteService` or `AuthContextService` read method, never through its repository. `AttemptEvaluationsResponse` also gains `evaluationStatus`.

## 4. Assessment and context (backend)

- **Toggle removal (Decision 2.32):** remove the field from the Studio form, the create DTO validation, `InterviewerContextInput` and `PromptTemplates`. Keep the column. The API ignores the field when an old client sends it. Follow-ups up to `followUpLimit` stay unchanged. Remove the `shortcut:` comment in `AttemptStateMachine` that names invented questions.
- **`followUpLimit = 0` prompt:** for the active core question, the prompt says "Ask this question now. Do not ask a follow-up after the answer." The words "the next question comes next" must not appear before the core question is asked.
- **Prompt-build failure:** move `context.buildInterviewerContext(...)` inside the `try` in `AssessmentSessionService.streamInterviewer`. A build failure then falls back to the configured question at once (Decision 2.29). The `WARN` log names the attempt and the exception class.
- **SSE executor:** a bounded `ThreadPoolTaskExecutor` bean for streaming (`nifty.assessment.stream.core-size: 8`, `max-size: 32`, `queue-capacity: 100`). A rejected task answers `503` ProblemDetail. A boot test asserts the bean type and bounds.
- **Expire and evaluate (Decision 2.33):** `AttemptExpiryScheduler` may expire an attempt with the interview done and BFI-10 open. It then completes the attempt with no soft profile. It publishes `AttemptCompletedEvent` after commit.
- **Attempts left:** `SessionStateResponse.attemptsLeft` (int), for the retake UI.

## 5. Auth, invites, Studio (backend)

- **Re-invite (Decision 2.34):** `POST …/invites` for a revoked `(config, candidateRef)` rotates the token on the same row: a new hash, `revoked = false`, and audit `INVITE_REISSUED`. Attempts and results stay linked to the same invite, so the attempt limit still counts. Old sessions must stay dead: `CandidateSessionPrincipal` gains `tokenHash`, and the per-request check becomes `existsByIdAndTokenHashAndRevokedFalse`. No migration. A non-revoked duplicate still answers `409`.
- **Concurrent creates:** invites and Studio catch `DataIntegrityViolationException` on the unique key. Invites answer `409`. Studio re-reads by `Idempotency-Key` and replays (REQ-IS-01), or answers `400` per REQ-IS-12 when another professor owns the key.
- **CSV:** the invite CSV parser handles RFC 4180 quoted fields with commas, `""` escapes and line breaks. It stays hand-written with no new dependency.
- **CSRF renewal:** after a professor login or an invite login, the controller calls the configured `CsrfAuthenticationStrategy`. The fallback is the repository: delete the old token, save a new one. The old token answers `403` afterwards.
- **Login rate limit:** an in-memory limiter keyed by lowercase email. After 5 failures within 15 minutes, login answers `429` ProblemDetail with `Retry-After`. A success resets the count. Add `// shortcut: in-memory, single instance only; move to a shared store when the backend scales out`.
- **bcrypt limit:** `ProfessorBootstrap` fails startup with a clear message when the UTF-8 password is longer than 72 bytes. The message never contains the password.
- **List query:** the assessment list gets the counts in one grouped query, not one query per row. The test asserts the query count with the Hibernate statistics or a `@DataJpaTest`.
- **localStorage:** `stores/assessmentForm.ts` wraps `setItem` in `try/catch`. On a quota or privacy error, the form keeps working without persistence.

## 6. Teams and docs

- `recalculate` stores diagnostics at full precision, like `generate`. The UI rounds for display only.
- Tests: add `verify(repo).findByIdForUpdate(id)` for the recalculate and publish lock. Mark the three property tests with a comment `// design check, not required by spec`. Delete the assert that only re-checks its own setup. Merge the duplicated arrange blocks (about 40 lines) into one helper.
- `.env.example`: the comment above the key says the key, URL and model serve both the interviewer and the evaluator.
- `docs/setup.md` troubleshooting: "Interviewer call failed" in the backend log means the candidate saw the configured question text (Decision 2.29).
