import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import RankingView from './RankingView.vue'

describe('RankingView empty state (REQ-NAV-06)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('links to Studio when the professor has no assessment', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() =>
        Promise.resolve(new Response('[]', { headers: { 'Content-Type': 'application/json' } })),
      ),
    )
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/ranking', component: RankingView },
        { path: '/ranking/studio/new', component: { template: '<div />' } },
      ],
    })
    await router.push('/ranking')
    await router.isReady()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    const wrapper = mount(RankingView, {
      global: { plugins: [router, [VueQueryPlugin, { queryClient }], PrimeVue] },
    })
    await flushPromises()

    const link = wrapper.get('[data-testid="ranking-empty-studio-link"]')
    expect(link.attributes('href')).toBe('/ranking/studio/new')
  })
})
