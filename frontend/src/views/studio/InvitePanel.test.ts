import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import InvitePanel from './InvitePanel.vue'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const CONFIG_ID = '11111111-1111-1111-1111-111111111111'

function mountPanel() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return mount(InvitePanel, {
    props: { configId: CONFIG_ID },
    global: { plugins: [[VueQueryPlugin, { queryClient }], PrimeVue] },
  })
}

describe('InvitePanel (REQ-AA-03, REQ-AA-08)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('adds a single candidate and shows the generated invite link', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        jsonResponse(
          [{ inviteId: 'inv-1', candidateRef: 'alice@example.test', link: 'http://localhost:5173/invite/tok1' }],
          201,
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse([
          { inviteId: 'inv-1', candidateRef: 'alice@example.test', revoked: false, createdAt: '2026-11-26T10:00:00Z' },
        ]),
      )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('[data-testid="invite-single-input"]').setValue('alice@example.test')
    await wrapper.get('[data-testid="invite-single-submit"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/ranking/studio/configs/${CONFIG_ID}/invites`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ candidateRefs: ['alice@example.test'] }),
      }),
    )
    expect(wrapper.text()).toContain('http://localhost:5173/invite/tok1')
  })

  it('imports a CSV of 3 candidate rows and posts all 3 candidateRefs', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        jsonResponse(
          [
            { inviteId: 'i1', candidateRef: 'cand_1', link: 'http://localhost:5173/invite/t1' },
            { inviteId: 'i2', candidateRef: 'cand_2', link: 'http://localhost:5173/invite/t2' },
            { inviteId: 'i3', candidateRef: 'cand_3', link: 'http://localhost:5173/invite/t3' },
          ],
          201,
        ),
      )
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mountPanel()
    await flushPromises()

    const csvFile = new File(['candidate_ref\ncand_1\ncand_2\ncand_3\n'], 'students.csv', { type: 'text/csv' })
    const input = wrapper.get('[data-testid="invite-csv-input"]').element as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [csvFile], configurable: true })
    await wrapper.get('[data-testid="invite-csv-input"]').trigger('change')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/ranking/studio/configs/${CONFIG_ID}/invites`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ candidateRefs: ['cand_1', 'cand_2', 'cand_3'] }),
      }),
    )
    expect(wrapper.text()).toContain('http://localhost:5173/invite/t3')
  })

  it('revokes an active invite by calling DELETE', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse([
          { inviteId: 'inv-9', candidateRef: 'bob@example.test', revoked: false, createdAt: '2026-11-26T10:00:00Z' },
        ]),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        jsonResponse([
          { inviteId: 'inv-9', candidateRef: 'bob@example.test', revoked: true, createdAt: '2026-11-26T10:00:00Z' },
        ]),
      )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('[data-testid="revoke-invite-inv-9"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/ranking/studio/configs/${CONFIG_ID}/invites/inv-9`,
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(wrapper.text()).toContain('Revoked')
  })
})
