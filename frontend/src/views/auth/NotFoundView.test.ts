import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore, type AuthMe } from '@/stores/auth'
import NotFoundView from './NotFoundView.vue'
import authRoutes from './routes'

// REQ-NAV-10: an unknown path shows "Page not found" with a link to the role start page.
async function mountNotFound(me: AuthMe | null) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().me = me
  const router = createRouter({ history: createMemoryHistory(), routes: [...authRoutes] })
  await router.push('/rankng')
  await router.isReady()
  return { router, wrapper: mount(NotFoundView, { global: { plugins: [pinia, router] } }) }
}

describe('NotFoundView (REQ-NAV-10)', () => {
  it('matches an unknown path and offers the professor a link to Studio', async () => {
    const { router, wrapper } = await mountNotFound({ role: 'PROFESSOR', professorId: 'p1' })

    expect(router.currentRoute.value.matched[0].components?.default).toBe(NotFoundView)
    expect(wrapper.text()).toContain('Page not found')
    const link = wrapper.get('[data-testid="not-found-home"]')
    expect(link.attributes('href')).toBe('/ranking/studio')
    expect(link.text()).toContain('Studio')
  })

  it('links a candidate to the interview and a visitor to sign-in', async () => {
    const candidate = await mountNotFound({ role: 'CANDIDATE', professorId: null })
    expect(candidate.wrapper.get('[data-testid="not-found-home"]').attributes('href')).toBe('/assessment')

    const visitor = await mountNotFound(null)
    expect(visitor.wrapper.get('[data-testid="not-found-home"]').attributes('href')).toBe('/sign-in')
  })
})
