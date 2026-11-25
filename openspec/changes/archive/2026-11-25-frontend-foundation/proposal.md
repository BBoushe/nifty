# Proposal: Frontend Foundation (`frontend-foundation`)

## Context & Problem

The teammate tickets need a frontend test runner, the PrimeVue design system, one router file per feature and a shared API client. None of these exist. Without them, four tickets would edit the same router and build their own error and SSE handling.

## Scope

- US-FF-01: Vitest, `@vue/test-utils`, `jsdom`, an ECharts mock, `npm run test`, and the test step in both CI files.
- US-FF-02: PrimeVue with one preset built from Tailwind v4 `@theme` tokens; `ScoreBadge` for levels `0..4` and hatched `N/A`.
- US-FF-03: views in feature folders; one `routes.ts` per feature, spread into `router/index.ts`.
- US-FF-04: shared API client for RFC 9457 errors and SSE streams from a `POST`.

## Out of Scope

- Feature views (teammate tickets). Backend work (`backend-foundation`).
