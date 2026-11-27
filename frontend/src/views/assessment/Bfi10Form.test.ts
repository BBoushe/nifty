import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Bfi10Form from './Bfi10Form.vue'

const ATTEMPT = '11111111-1111-1111-1111-111111111111'

function mountForm() {
  const queryClient = new QueryClient()
  return mount(Bfi10Form, {
    props: { attemptId: ATTEMPT },
    global: { plugins: [[VueQueryPlugin, { queryClient }], PrimeVue] },
  })
}

const answerAll = async (wrapper: ReturnType<typeof mountForm>, skip?: number) => {
  for (let item = 1; item <= 10; item++) {
    if (item !== skip) await wrapper.get(`[data-testid="bfi10-item-${item}-3"]`).setValue(true)
  }
}

describe('Bfi10Form (REQ-AC-14, Decision 4.3)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the 10 fixed items on a 5-point scale under the stem', () => {
    const wrapper = mountForm()

    expect(wrapper.text()).toContain('I see myself as someone who…')
    expect(wrapper.findAll('[data-testid^="bfi10-item-"][data-testid$="-1"]')).toHaveLength(10)
    expect(wrapper.text()).toContain('Disagree strongly')
    expect(wrapper.text()).toContain('Agree strongly')
  })

  it('names a missing item and sends nothing', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountForm()

    await answerAll(wrapper, 7)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="bfi10-error"]').text()).toContain('7')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('posts the 10 answers and emits submitted on 204', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mountForm()

    await answerAll(wrapper)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`/api/v1/assessment/sessions/${ATTEMPT}/bfi10`)
    expect(JSON.parse(init.body)).toEqual({ items: [3, 3, 3, 3, 3, 3, 3, 3, 3, 3] })
    expect(wrapper.emitted('submitted')).toHaveLength(1)
  })
})
