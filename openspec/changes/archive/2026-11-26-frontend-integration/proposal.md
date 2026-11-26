# Proposal: Frontend Integration (`frontend-integration`)

## Context & Problem

Tickets 01–05 built every page, but each ticket could edit only its own `views/<feature>/` folder (`docs/tickets/00-parallel-waves-and-contracts.md`). Nobody owned the shared shell. So the header still shows the Wave 0 links "Ranking View" and "Assessment View", and `/` always opens `/ranking`. Studio, Team Builder, results and sign-out have no links. A user who opens the app cannot find most of it.

## Scope

- Role-aware header in `frontend/src/App.vue`: professor links, candidate links, sign in, and sign out.
- `/` sends each role to its own start page.
- Links between pages. Studio detail links to the ranking and team builder of its assessment. The ranking empty state links to Studio. The finished interview links to the results page.
- Professor sign-in without a `redirect` opens Studio.

## Out of Scope

- New pages, new API endpoints, or backend changes.
- Visual redesign of existing pages.
- The pre-cohort fixes from the PR #28 review (separate fixes change).
