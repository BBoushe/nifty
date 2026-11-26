import { createRouter, createWebHistory } from 'vue-router'
import assessmentRoutes from '@/views/assessment/routes'
import { authGuard } from '@/views/auth/guard'
import authRoutes from '@/views/auth/routes'
import rankingRoutes from '@/views/ranking/routes'
import studioRoutes from '@/views/studio/routes'
import teamsRoutes from '@/views/teams/routes'

// Each feature folder owns its routes file, so a ticket edits only its own routes (US-FF-03).
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/ranking' },
    ...authRoutes,
    ...studioRoutes,
    ...assessmentRoutes,
    ...rankingRoutes,
    ...teamsRoutes,
  ],
})

router.beforeEach(authGuard)

export default router
