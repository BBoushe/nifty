---
trigger: glob
globs: "frontend/**"
description: Vue 3.5 (stable), TypeScript, Vite 8, Tailwind CSS v4, Pinia, TanStack Query, and ECharts rules for frontend changes.
---

# Frontend Vue / TypeScript Rules

> Also follow canonical [`.agents/rules/coding-standards.md`](./coding-standards.md) (ETC/DRY, avoid bespoke one-off components, use HOFs cleanly, and organize views into domain/feature folders under `frontend/src/views/<feature>/`).

1. **Framework Pin:** Stay on **Vue 3.5 stable** + **Vite 8** + **TypeScript strict mode**. **NEVER** upgrade to Vue 3.6 RC / Vapor Mode without an explicit team ADR in `docs/decisions/index.md`.
2. **State & Data Fetching:**
   - Use **TanStack Query (`@tanstack/vue-query`)** for server state and backend API calls. Every TanStack Query view MUST explicitly render `loading`, `empty`, `success`, and `error` states.
   - Use **Pinia** only for client/UI state (e.g., active filters, drawer selection, drag-and-drop staging).
   - Use **Vue Router** separating Assessment View (`/assessment/*`) and Ranking View (`/ranking/*`).
3. **Styling & Visualisation:**
   - Use **Tailwind CSS v4** utilities. Do not install PrimeVue or shadcn-vue until team Decision O1 (`docs/decisions.md`) is finalized.
   - Use **Apache ECharts (`vue-echarts`)** for the Skills Matrix heatmap, Cohort Gap charts, and Team Balance charts.
4. **Evidence-First UX Rules:**
   - **Zero client-side scoring math:** Never recompute or duplicate fit, coverage, confidence, or ranking math in TypeScript/Vue — render backend-computed values only.
   - Visually distinguish `N/A` (hatched pattern) from low scores (`0`/`1`). Never render `N/A` as `0`.
   - Display `fit` and `coverage` separately.
   - During an active Assessment Chat, NEVER render scores, weights, or formulas.
   - Flagged cells (below lower confidence bound) MUST highlight clearly and block bulk-approve.
5. **Verification:** Before finishing frontend edits, run `npm run typecheck && npm run lint && npm run build` inside `frontend/`.
