import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiProblem } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import SignInView from './SignInView.vue'

async function mountSignIn(initialPath = '/sign-in') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sign-in', component: SignInView },
      { path: '/ranking', component: { template: '<div>ranking</div>' } },
    ],
  })
  await router.push(initialPath)
  await router.isReady()
  const wrapper = mount(SignInView, {
    global: {
      plugins: [pinia, router, PrimeVue],
    },
  })
  return { wrapper, auth: useAuthStore(), router }
}

describe('SignInView (US-AU-02)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('calls auth.login with email and password and navigates to /ranking', async () => {
    const { wrapper, auth, router } = await mountSignIn()
    const loginSpy = vi.spyOn(auth, 'login').mockResolvedValue()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('input[type="email"]').setValue('prof@example.test')
    await wrapper.get('input[type="password"]').setValue('synthetic-secret')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    expect(loginSpy).toHaveBeenCalledWith('prof@example.test', 'synthetic-secret')
    expect(pushSpy).toHaveBeenCalledWith('/ranking')
  })

  it('shows the 401 ProblemDetail detail when sign-in fails', async () => {
    const { wrapper, auth } = await mountSignIn()
    vi.spyOn(auth, 'login').mockRejectedValue(
      new ApiProblem(401, 'Unauthorized', 'The email or password is not correct.'),
    )

    await wrapper.get('input[type="email"]').setValue('prof@example.test')
    await wrapper.get('input[type="password"]').setValue('wrong-pass')
    await wrapper.get('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('The email or password is not correct.')
  })
})
