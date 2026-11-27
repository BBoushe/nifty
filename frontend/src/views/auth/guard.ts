import type { NavigationGuard } from 'vue-router'
import { type UserRole, useAuthStore } from '@/stores/auth'

function requiredRoleFor(path: string): UserRole | null {
  if (path === '/ranking' || path.startsWith('/ranking/')) return 'PROFESSOR'
  if (path === '/assessment' || path.startsWith('/assessment/')) return 'CANDIDATE'
  return null
}

/** REQ-NAV-04: each role's start page; no role means sign in. */
export function startPageFor(role: UserRole | undefined): string {
  if (role === 'PROFESSOR') return '/ranking/studio'
  if (role === 'CANDIDATE') return '/assessment'
  return '/sign-in'
}

/** Decision 1.15 / US-AU-02: /ranking/* needs PROFESSOR, /assessment/* needs CANDIDATE. */
export const authGuard: NavigationGuard = async (to) => {
  const required = requiredRoleFor(to.path)
  if (!required) return true

  const auth = useAuthStore()
  if (!auth.loaded) await auth.fetchMe()
  if (auth.me?.role === required) return true
  // REQ-NAV-09: one browser holds one session; a signed-in user of the other role goes home, not to sign-in.
  if (auth.me) return { path: startPageFor(auth.me.role) }

  return { path: '/sign-in', query: { redirect: to.fullPath } }
}
