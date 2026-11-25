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
   - Use **Vue Router** to separate Assessment View (`/assessment/*`) and Ranking View (`/ranking/*`).
3. **Styling & Visualisation:**
   - Use **Tailwind CSS v4** utilities and **PrimeVue** components themed from the design tokens in `docs/design_system.md` (Decision 1.14). Do not add a second component kit.
   - Use **Apache ECharts (`vue-echarts`)** for the Skills Matrix heatmap, Cohort Gap charts, and Team Balance charts.
4. **Evidence-First UX Rules:**
   - **Zero client-side scoring math:** Never recompute or duplicate fit, coverage, confidence, or ranking math in TypeScript/Vue. Render backend-computed values only.
   - Visually distinguish `N/A` (hatched pattern) from low scores (`0`/`1`). Never render `N/A` as `0`.
   - Display `fit` and `coverage` separately.
   - During an active Assessment Chat, NEVER render scores, weights, or formulas.
   - Flagged cells (confidence below the question's threshold, paste-suspected, or low coverage) MUST highlight clearly and block bulk-approve.
   - Display the per-question countdown from the server's `expiresAt`. Submit the current text automatically at zero. Never offer pause or resume (Decision 2.14).
5. **Verification:** Before finishing frontend edits, run `npm run typecheck && npm run test && npm run lint && npm run build` inside `frontend/`.
6. Use <script setup lang="ts"> and Composition API. No Options API, no defineComponent unless needed.
7. .value rules: required in script, never in template. Don't write {{ count.value }}.
8. Don't destructure reactive objects or props without toRefs/toRef (or the 3.5 props destructure).
9. Never mutate props. Use defineModel or emits.
10. Prefer computed over watch for derived state. Use watch only for side effects.
11. Always :key in v-for, stable and unique, never the index when the list reorders.
12. No v-if with v-for on the same element.
13. Use defineProps/defineEmits/defineModel macros without importing them (they're compiler macros).
14. Event naming: emit in camelCase or kebab-case consistently. Listen with @kebab-case. Component names PascalCase, multi-word (UserCard.vue).
15. Styling: <style scoped>. Use :deep() to reach child internals, v-bind() in CSS for reactive values.
16. Prefer Pinia for shared state, with storeToRefs when destructuring a store's state (actions can be destructured directly).
17. Cleanup: clear timers and listeners in onUnmounted or onWatcherCleanup.
18. Use shallowRef for big objects or third-party instances (maps, charts, editors).
19. Typing: ref<string | null>(null), PropType is unnecessary with type-based defineProps, and run vue-tsc for type checks (plain tsc can't read .vue).
20. Testing: Vitest + @vue/test-utils (mount, shallowMount) and await nextTick() after state changes.
22. React habits to ban: no useState/useEffect patterns, no className, no onClick (use @click), no JSX unless explicitly configured. Also ban key on non-list elements for forced remounting without good reason.

## Quick gotchas
1. Vue's reactivity is proxy-based: replacing reactive object references breaks tracking. Assigning to ref.value is fine.
2. DOM updates are async: use await nextTick() to read the DOM after a state change.
3. Arrays and Maps/Sets are reactive, so mutate with push, splice etc. directly.
4. Template expressions should be simple. Move logic to computed.
5. v-html is an XSS risk, same as dangerouslySetInnerHTML.
