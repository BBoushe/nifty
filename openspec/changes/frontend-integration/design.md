# Design: Frontend Integration

- **Auth state in the header.** `App.vue` reads `useAuthStore().me`. It calls `fetchMe()` once on mount, because public routes (`/sign-in`, `/invite/:token`) do not run the route guard. The header renders from `me.role`, so no second source of truth exists.
- **Role start page.** `/` uses a function redirect in `router/index.ts`. It awaits `fetchMe()` and returns `/ranking/studio`, `/assessment` or `/sign-in`. Each role's routes keep their existing guard (Decision 1.15).
- **Results link.** The candidate's `configId` comes from `GET /api/v1/auth/me` (`AuthMe.configId`, added by `invite-links`). No new endpoint.
- **Sign out.** `authStore.logout()` already exists. The header calls it, then `router.push('/sign-in')`.
- **Links.** Plain `RouterLink` elements with the existing button classes. No new shared component, because each link appears once (coding standard #5).
- **Ownership.** The ticket-era rule that no ticket edits `App.vue` is lifted: one contributor now owns the whole app.
