import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiProblem } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import InviteLandingView from './InviteLandingView.vue'

async function mountInviteLanding(token: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/invite/:token', component: InviteLandingView },
      { path: '/assessment', component: { template: '<div>assessment</div>' } },
    ],
  })
  await router.push(`/invite/${token}`)
  await router.isReady()
  const auth = useAuthStore()
  return { pinia, router, auth }
}

describe('InviteLandingView (REQ-AA-04)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('signs in with the invite token and redirects to /assessment', async () => {
    const { pinia, router, auth } = await mountInviteLanding('valid-token-xyz')
    const loginSpy = vi.spyOn(auth, 'loginWithInvite').mockResolvedValue({ configId: 'cfg-1' })
    const replaceSpy = vi.spyOn(router, 'replace')

    mount(InviteLandingView, { global: { plugins: [pinia, router, PrimeVue] } })
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith('valid-token-xyz')
    expect(replaceSpy).toHaveBeenCalledWith('/assessment')
  })

  it('shows "This link is not valid." on 401', async () => {
    const { pinia, router, auth } = await mountInviteLanding('bad-token')
    vi.spyOn(auth, 'loginWithInvite').mockRejectedValue(
      new ApiProblem(401, 'Unauthorized', 'This link is not valid.'),
    )

    const wrapper = mount(InviteLandingView, { global: { plugins: [pinia, router, PrimeVue] } })
    await flushPromises()

    expect(wrapper.text()).toContain('This link is not valid.')
  })
})
