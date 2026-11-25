# Design: Frontend Foundation

## 1. Sources

`docs/feature_backlog.md` §2 (US-FF-01..04), `docs/design_system.md`, `.agents/rules/frontend-vue.md`, `docs/api.md` §3 (error envelope), `docs/tickets/00-parallel-waves-and-contracts.md` (shared files).

## 2. Decisions inside this change

- **Pins:** keep `vue ~3.5`, `vite ^8` and the existing majors. New packages use the latest stable version that supports them and is at least two weeks old.
- **Tests:** `vitest` with `environment: 'jsdom'` in `vite.config.ts` (or `vitest.config.ts`); tests live next to the code as `*.test.ts`; `src/test/echarts-mock.ts` stubs `vue-echarts` for component tests. Script: `"test": "vitest run"`.
- **CI:** add `npm run test` after `npm run lint` in the GitHub `Frontend` job and the GitLab `frontend` job, in the same position, so the CI drift test passes.
- **Design system:** tokens as Tailwind `@theme` variables in `src/style.css`; one PrimeVue preset (`src/theme/preset.ts`) maps them. `src/components/ScoreBadge.vue` shows `<level>/4 · <label>` with the level fills of `docs/design_system.md`, and `N/A · not enough evidence` with a diagonal hatch. It never shows `N/A` as `0`.
- **PrimeVue version:** `primevue` 4.5.5 and `@primeuix/themes` 2.0.3 (both MIT, pinned exact). PrimeVue 5 needs a license key, so the build does not use it. Re-check when the team wants v5. `ScoreBadge` is a plain `span` styled with the `@theme` tokens, because a PrimeVue `Tag` adds styles that conflict with the level fills. Dark mode and the Plus Jakarta Sans font file are out of scope for this change (no external font request).
- **Routes:** `src/views/{auth,studio,assessment,ranking,teams}/routes.ts` each export a `RouteRecordRaw[]`. `router/index.ts` spreads them. Existing paths stay the same. `RankingView.vue` and `AssessmentView.vue` move to `views/ranking/` and `views/assessment/`.
- **API client:** `src/api/client.ts` exports `apiFetch<T>()` (throws `ApiProblem` with `status`, `title`, `detail`, `errors[]` on `application/problem+json`) and `postSse()` (async iterator of SSE events from a `POST` via `fetch` + `ReadableStream`). `health.ts` uses `apiFetch`.
