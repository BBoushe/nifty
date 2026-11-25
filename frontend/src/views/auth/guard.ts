import type { NavigationGuard } from 'vue-router'
import { type UserRole, useAuthStore } from '@/stores/auth'

function requiredRoleFor(path: string): UserRole | null {
  if (path === '/ranking' || path.startsWith('/ranking/')) return 'PROFESSOR'
  if (path === '/assessment' || path.startsWith('/assessment/')) return 'CANDIDATE'
  return null
}

/** Decision 1.15 / US-AU-02: /ranking/* needs PROFESSOR, /assessment/* needs CANDIDATE. */
export const authGuard: NavigationGuard = async (to) => {
  const required = requiredRoleFor(to.path)
  if (!required) return true

  const auth = useAuthStore()
  if (!auth.loaded) await auth.fetchMe()
  if (auth.me?.role === required) return true

  return { path: '/sign-in', query: { redirect: to.fullPath } }
}
