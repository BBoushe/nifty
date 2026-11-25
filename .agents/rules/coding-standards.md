---
trigger: always_on
description: Centralized living coding standards (ETC/DRY, small testable functions, hotspot performance vs Streams, living docs/api.md, minimal reusable classes, frontend reuse/HOFs/feature-grouped views) enforced by builders and cold reviewers.
---

# Living Coding Standards (Canonical)

This file is the **single canonical home** for coding rules in all harnesses. The harnesses are Antigravity, Claude Code (through the `@` import in `CLAUDE.md`), Codex, Kiro Crew, and T3 Code (through `AGENTS.md`). When the team or a cold reviewer agrees on a new coding convention during the sprint, append it here. Also record a `docs/decisions/3-team-process.md` entry if the convention is non-trivial.

## Core Rules

1. **ETC & DRY (Easy to Change, Don't Repeat Yourself):**
   - Structure every module so future changes touch **one** place.
   - Extract shared validation, mapping, formatting, and domain logic into centralized reusable helpers instead of copy-pasting across services, controllers, or components.
   - *Reviewer level:* **Blocking** on clear copy-pasted logic or duplicated domain rules.

2. **Small, Abstractable, Testable Functions:**
   - Keep functions short, single-purpose, pure where possible, and independently unit-testable without heavy framework bootstrapping.
   - Prefer explicit inputs/outputs over hidden mutation.
   - *Reviewer level:* **Convention Candidate** for subjective splitting taste. **Blocking** if a monolithic function prevents testing a required domain invariant.

3. **Hotspot Performance — Avoid Streams in Repeated / Hot Paths:**
   - Do **not** use Java Streams (`stream().map().filter()...`) in repeatedly called, hot-path, or centralized shared helper functions. Examples: quote verification loops, formula/confidence calculators, the Simulated Annealing team swap loop, batch transcript scans.
   - Write clean, optimized, low-allocation loops for hotspots and shared utilities. Use Java Streams strictly for infrequent or one-off in-place transformations.
   - *Reviewer level:* **Blocking** on Streams inside hot-path or centralized repeatedly called helpers.

4. **Living API Documentation (`docs/api.md`):**
   - Keep [`docs/api.md`](../../docs/api.md) synchronized in the **same PR** whenever any backend controller endpoint, HTTP method/path, role guard, request/response DTO, error envelope, or SSE event changes. The file complements live `springdoc-openapi` (`/v3/api-docs` & `/swagger-ui.html`).
   - *Reviewer level:* **Blocking** if a PR adds/changes an API endpoint without updating `docs/api.md`.

5. **Minimal Code Footprint & Simple Reusable Classes:**
   - Always reuse existing simple classes, records, and utilities wherever possible to keep the codebase small.
   - **Never** create a second class, helper, DTO, or interface that re-implements existing logic under a different name. Avoid speculative boilerplate abstractions.
   - *Reviewer level:* **Blocking** on duplicate classes/interfaces re-implementing existing logic.

6. **Frontend Reuse, HOFs & Feature-Grouped Views:**
   - **Reuse & centralize:** Avoid granular bespoke one-off components. Build and reuse shared primitives, layouts, and composables.
   - **Higher-Order Functions (HOFs):** Use HOFs (e.g., query wrappers, formatter/validator factories, event/error handlers) wherever they cleanly remove repetition.
   - **Feature-grouped views:** Organize Vue views logically inside domain/feature folders under `frontend/src/views/<feature>/` (e.g., `views/assessment/`, `views/ranking/`, `views/studio/`, `views/teams/`) for predictable navigation.
   - *Reviewer level:* **Blocking** on un-grouped bespoke views that violate feature folders, or on duplicated components. **Convention Candidate** on optional HOF refactors.

## Traceability & Living Growth Protocol

- **Decision Pointers:** For non-obvious design, performance, or domain choices, add a concise inline comment (`// Decision X.Y: <short why>`) pointing to `docs/decisions/`.
- **Appending New Rules:** Add new numbered rules to this file as concise bullets with an explicit `Reviewer level` (`Blocking` vs. `Convention Candidate`). Never duplicate them into `.claude/rules/` or `.kiro/steering/`.
