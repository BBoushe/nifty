# Proposal: MVP Hardening (`mvp-hardening`)

## Context & Problem

The MVP is on `dev` (`d0480e6`). The first manual runs, an audit of `dev` and the cold reviews of PRs #26–#34 found bugs and gaps. None of them blocks a demo. Together they block a real cohort.

The professor review is hard to use:

- The Evidence Drawer opens below the student list. The professor must scroll away from the list to see one student.
- The conversation shows as a list of raw records with ISO timestamps. It is hard to read.

Other problems found:

- **Evaluation:** the judge parser rejects JSON in a code fence. A run that crashes on its last try stays `RUNNING`, and a run that used all tries cannot run again. A crash before enqueue drops the completion event. The first run scores with the live Studio weights, not the snapshot. Approve or delete at the same time as a release can deadlock.
- **Release:** a SOFT_SKILLS-only attempt never auto-releases, so Team Builder never places it (Decision 2.31).
- **Assessment:** the invented-questions toggle does nothing (Decision 2.32). An attempt with BFI-10 that expires is never evaluated (Decision 2.33). With `followUpLimit = 0`, the prompt can skip the core question. A prompt-build failure hangs the attempt for 240 s. The SSE executor may be unbounded. The candidate cannot start a retake.
- **Frontend:** no refresh while an evaluation runs. Release is enabled on unfinished runs. Ranking ignores route changes and shows UUID prefixes, not names. There is no 404 page and no Teams link from Ranking. "Send to students" does not scroll. A stale cache key keeps deleted assessments in Ranking. A backend outage shows a blank page. A failed sign-out shows no message.
- **Auth, invites, Studio:** a revoked student cannot get a new link (Decision 2.34). Concurrent creates give `500`. The CSV import splits a quoted line break. The CSRF token is not renewed after login. Login has no rate limit. The bootstrap password is not checked against the 72-byte bcrypt limit. The assessment list runs one count query per row. A failed `localStorage` write throws.
- **Teams:** recalculate rounds diagnostics, generate does not. Some tests prove nothing or repeat each other.

## Scope

1. **Professor review in modals:** the Evidence Drawer opens as a modal dialog over the Ranking page. A shared Transcript dialog shows the conversation as a readable chat. The professor opens it from the Evidence modal. The candidate opens it from the results page.
2. **Frontend fixes:** polling, the Release button state, route sync, candidate names and the retake button. Also a 404 route, a Ranking → Teams link, the invites scroll, the cache key, the backend-down page and the sign-out error.
3. **Evaluation fixes:** tolerant judge JSON parsing with temperature 0, final-try crash handling and a professor re-run endpoint. Also recovery of lost events, snapshot weights, one lock order, soft-only auto-release and candidate names in the matrix.
4. **Assessment and context fixes:** remove the invented-questions toggle (LLM follow-ups stay) and expire-and-evaluate BFI-10 attempts. Fix the `followUpLimit = 0` prompt. Fall back at once on a prompt-build failure. Bound the SSE executor and log interviewer fallbacks.
5. **Auth, invites and Studio fixes:** re-invite after revoke, `409` instead of `500` on concurrent creates, and CSV quoted line breaks. Also CSRF renewal on login, a login rate limit, the bcrypt limit check, one list query and safe `localStorage` writes.
6. **Teams and docs:** consistent diagnostics precision, the test clean-up, the `.env.example` comment and removal of the unused `schema.d.ts`.

## Out of Scope

- The unused interviewer prompt preview endpoint (REQ-CC-08). It stays in the API. No UI uses it yet.
- More Team Builder options (`alpha`, `seed`, trait weights) and a proposal history picker.
- More formula presets. One preset stays.
- `promptedTerms` / `unpromptedTerms` in the transcript export. They stay empty arrays.
- The revocation check on each candidate request. REQ-AA-08 needs it.
- Separate models or keys for the interviewer and the evaluator.
- Implementing invented questions (Decision 2.32 removes the toggle instead).
