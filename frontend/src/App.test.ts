import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { type AuthMe, useAuthStore } from '@/stores/auth'
import App from './App.vue'

const CONFIG = '22222222-2222-2222-2222-222222222222'
const stub = { template: '<div />' }

// abortFirstNavigation: the route guard fails once, like authGuard does when fetchMe cannot reach the server.
async function mountApp(me: AuthMe | null, fetchMeImpl?: () => Promise<AuthMe | null>, abortFirstNavigation = false) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  const fetchMe = vi.spyOn(auth, 'fetchMe').mockImplementation(
    fetchMeImpl ??
      (async () => {
        auth.me = me
        return me
      }),
  )
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: stub },
      { path: '/sign-in', component: stub },
    ],
  })
  if (abortFirstNavigation) {
    let failed = false
    router.beforeEach(() => {
      if (failed) return true
      failed = true
      throw new TypeError('Failed to fetch')
    })
    router.push('/').catch(() => undefined)
  } else {
    await router.push('/')
    await router.isReady()
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(App, { global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }], PrimeVue] } })
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

  it('shows Interview and Results, and no Sign out, for a candidate (Decision 2.36)', async () => {
    const { wrapper } = await mountApp({ role: 'CANDIDATE', professorId: null, configId: CONFIG })

    expect(ids(wrapper)).toEqual(['nav-interview', 'nav-results'])
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

  it('shows "Server not reachable" on a network error and Retry loads the session again (REQ-NAV-13)', async () => {
    let attempt = 0
    const { wrapper, auth, fetchMe } = await mountApp(null, async () => {
      if (attempt++ === 0) throw new TypeError('Failed to fetch')
      auth.me = { role: 'PROFESSOR', professorId: 'p1' }
      return auth.me
    })

    expect(wrapper.get('[data-testid="server-down"]').text()).toContain('Server not reachable')
    expect(fetchMe).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="server-retry"]').trigger('click')
    await flushPromises()

    expect(fetchMe).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="server-down"]').exists()).toBe(false)
    expect(ids(wrapper)).toContain('nav-studio')
  })

  it('Retry runs the first navigation again when the failed first load aborted it (REQ-NAV-13)', async () => {
    window.history.replaceState({}, '', '/sign-in')
    let attempt = 0
    const { wrapper, router } = await mountApp(
      null,
      async () => {
        if (attempt++ === 0) throw new TypeError('Failed to fetch')
        return null
      },
      true,
    )
    expect(router.currentRoute.value.matched).toHaveLength(0)

    await wrapper.get('[data-testid="server-retry"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/sign-in')
    window.history.replaceState({}, '', '/')
  })

  it('shows "Sign-out failed. Try again." on a 500 from logout and stays on the page (REQ-NAV-13)', async () => {
    const { wrapper, router } = await mountApp({ role: 'PROFESSOR', professorId: 'p1' })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ status: 500, title: 'Internal Server Error', detail: 'boom' }), {
          status: 500,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    )
    const push = vi.spyOn(router, 'push')

    await wrapper.get('[data-testid="nav-sign-out"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="sign-out-error"]').text()).toContain('Sign-out failed. Try again.')
    expect(push).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/')
    expect(ids(wrapper)).toContain('nav-sign-out')
  })
})
