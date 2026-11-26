import { createRouter, createWebHistory } from 'vue-router'
import assessmentRoutes from '@/views/assessment/routes'
import { authGuard, startPageFor } from '@/views/auth/guard'
import authRoutes from '@/views/auth/routes'
import rankingRoutes from '@/views/ranking/routes'
import studioRoutes from '@/views/studio/routes'
import teamsRoutes from '@/views/teams/routes'
import { useAuthStore } from '@/stores/auth'

// REQ-NAV-04: vue-router awaits no redirect function, so a beforeEnter guard picks the role start page.
async function startPage() {
  const me = await useAuthStore().fetchMe()
  return startPageFor(me?.role)
}

// Each feature folder owns its routes file, so a ticket edits only its own routes (US-FF-03).
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', beforeEnter: startPage, component: { render: () => null } },
    ...authRoutes,
    ...studioRoutes,
    ...assessmentRoutes,
    ...rankingRoutes,
    ...teamsRoutes,
  ],
})

router.beforeEach(authGuard)

export default router
