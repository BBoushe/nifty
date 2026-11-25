---
name: frontend-vue-check
description: Validate the Vue 3.5 TypeScript Vite 8 frontend (typecheck, ESLint, production build, ECharts, and Vue 3.5 pin).
---

# Skill: Frontend Vue Check

Use this skill before committing or reviewing any change under `frontend/`.

## Team Settings (Update via First-Run Bootstrap once resolved)

- **UI Component Library:** PrimeVue, themed from the design tokens (Decision 1.14)
- **Vue Version Pin:** `^3.5.x` stable (**do not** upgrade to Vue 3.6 RC).

## Checklist & Verification Commands

1. Confirm `frontend/package.json` remains on Vue `3.5.x` and Vite `8.x`.
2. Run all checks:
   ```sh
   cd frontend && npm run typecheck && npm run lint && npm run build
   ```
3. Confirm `N/A` scores are visually distinct (hatched) and never coerced to `0`, `fit` and `coverage` are shown separately, and Assessment Chat never leaks scores mid-interview.
