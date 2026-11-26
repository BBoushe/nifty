import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authGuard } from './guard'

const routeTo = (path: string): RouteLocationNormalized =>
  ({ path, fullPath: path }) as RouteLocationNormalized

describe('authGuard (US-AU-02)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('redirects an unauthenticated visitor from /ranking to /sign-in', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'fetchMe').mockResolvedValue(null)

    const result = await authGuard(routeTo('/ranking/studio'), routeTo('/'), vi.fn())

    expect(result).toEqual({ path: '/sign-in', query: { redirect: '/ranking/studio' } })
  })

  it('redirects a CANDIDATE from /ranking to /sign-in', async () => {
    const auth = useAuthStore()
    auth.me = { role: 'CANDIDATE', professorId: null }
    auth.loaded = true

    const result = await authGuard(routeTo('/ranking'), routeTo('/'), vi.fn())

    expect(result).toEqual({ path: '/sign-in', query: { redirect: '/ranking' } })
  })

  it('allows a PROFESSOR on /ranking routes', async () => {
    const auth = useAuthStore()
    auth.me = { role: 'PROFESSOR', professorId: '00000000-0000-0000-0000-000000000001' }
    auth.loaded = true

    const result = await authGuard(routeTo('/ranking/studio'), routeTo('/'), vi.fn())

    expect(result).toBe(true)
  })

  it('redirects a PROFESSOR from /assessment to /sign-in', async () => {
    const auth = useAuthStore()
    auth.me = { role: 'PROFESSOR', professorId: '00000000-0000-0000-0000-000000000001' }
    auth.loaded = true

    const result = await authGuard(routeTo('/assessment'), routeTo('/'), vi.fn())

    expect(result).toEqual({ path: '/sign-in', query: { redirect: '/assessment' } })
  })

  it('allows a CANDIDATE on /assessment routes', async () => {
    const auth = useAuthStore()
    auth.me = { role: 'CANDIDATE', professorId: null }
    auth.loaded = true

    const result = await authGuard(routeTo('/assessment/chat'), routeTo('/'), vi.fn())

    expect(result).toBe(true)
  })

  it('allows /sign-in without calling fetchMe', async () => {
    const auth = useAuthStore()
    const fetchSpy = vi.spyOn(auth, 'fetchMe')

    const result = await authGuard(routeTo('/sign-in'), routeTo('/'), vi.fn())

    expect(result).toBe(true)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it.each(['/ranking-evil', '/assessment-other'])('does not guard lookalike prefix %s', async (path) => {
    const auth = useAuthStore()
    const fetchSpy = vi.spyOn(auth, 'fetchMe')

    const result = await authGuard(routeTo(path), routeTo('/'), vi.fn())

    expect(result).toBe(true)
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
