# Design: `nov24-followups`

## 1. Timed-Out Answers (Decision 2.20)

| Ledger state | Evaluator input | Scores | Coverage |
|---|---|---|---|
| Answer before `expiresAt` | yes | from evidence | answered |
| Answer after `expiresAt`, within grace, with text (`timedOut = true`) | yes | from evidence | answered |
| Empty text, after grace (`409`), or no answer | no | `N/A` | unanswered |

`timedOut` is a marker in the conversation log only. It does not set `flagged`. A follow-up follows the same table. A core question counts as answered when any of its entries has answer text.

## 2. Roles (Decision 1.15, REQ-AA-01)

`Role` keeps one value per view. `SecurityConfig` uses `hasRole` for each view prefix. No controllers exist yet, so `RoleAccessTest` checks that an allowed request reaches dispatch (`404`) and a denied one stops at security (`403`). The test uses `SecurityMockMvcRequestPostProcessors.user()` from `spring-boot-starter-security-test` (`docs/ai_docs/spring-boot-4.md`).

## 3. Dependency Check

`curl` against `repo1.maven.org` returned `200` for `spring-boot-starter-webmvc` 4.0.10, `spring-boot-starter-data-jpa-test` 4.0.10, `spring-boot-starter-security-test` 4.0.10 and `timefold-solver-spring-boot-starter` 2.8.0. `docs/ai_docs/spring-ai-2-kiconnect.md` cites the move from `spring.ai.openai.chat.options.model` to `spring.ai.openai.chat.model`.
