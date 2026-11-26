import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { type AuthMe, useAuthStore } from '@/stores/auth'
import App from './App.vue'

const CONFIG = '22222222-2222-2222-2222-222222222222'
const stub = { template: '<div />' }

async function mountApp(me: AuthMe | null) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  const fetchMe = vi.spyOn(auth, 'fetchMe').mockImplementation(async () => {
    auth.me = me
    return me
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: stub },
      { path: '/sign-in', component: stub },
    ],
  })
  await router.push('/')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(App, { global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }]] } })
  await flushPromises()
  return { wrapper, auth, router, fetchMe }
}

const ids = (wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper']) =>
  wrapper.findAll('[data-testid^="nav-"]').map((el) => el.attributes('data-testid'))

describe('App header (REQ-NAV-01, 02, 03, 05)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { headers: { 'Content-Type': 'application/json' } })))
  })

  it('shows Studio, Ranking and Sign out for a professor, and loads the session once', async () => {
    const { wrapper, fetchMe } = await mountApp({ role: 'PROFESSOR', professorId: 'p1' })

    expect(ids(wrapper)).toEqual(['nav-studio', 'nav-ranking', 'nav-sign-out'])
    expect(wrapper.get('[data-testid="nav-studio"]').attributes('href')).toBe('/ranking/studio')
    expect(wrapper.get('[data-testid="nav-ranking"]').attributes('href')).toBe('/ranking')
    expect(fetchMe).toHaveBeenCalledTimes(1)
  })

  it('shows Interview, Results and Sign out for a candidate', async () => {
    const { wrapper } = await mountApp({ role: 'CANDIDATE', professorId: null, configId: CONFIG })

    expect(ids(wrapper)).toEqual(['nav-interview', 'nav-results', 'nav-sign-out'])
    expect(wrapper.get('[data-testid="nav-interview"]').attributes('href')).toBe('/assessment')
    expect(wrapper.get('[data-testid="nav-results"]').attributes('href')).toBe(`/assessment/results/${CONFIG}`)
  })

  it('shows only Sign in without a session', async () => {
    const { wrapper } = await mountApp(null)

    expect(ids(wrapper)).toEqual(['nav-sign-in'])
    expect(wrapper.get('[data-testid="nav-sign-in"]').attributes('href')).toBe('/sign-in')
  })

  it('signs out through the store and shows /sign-in', async () => {
    const { wrapper, auth, router } = await mountApp({ role: 'PROFESSOR', professorId: 'p1' })
    const logout = vi.spyOn(auth, 'logout').mockImplementation(async () => {
      auth.me = null
    })

    await wrapper.get('[data-testid="nav-sign-out"]').trigger('click')
    await flushPromises()

    expect(logout).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/sign-in')
    expect(ids(wrapper)).toEqual(['nav-sign-in'])
  })
})
