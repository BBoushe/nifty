import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { formatInvitesCsv, parseCandidateCsv } from '@/api/invites'
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
    await wrapper.get('form').trigger('submit')
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

  it('rejects blank single candidate submit without calling POST and renders 400 API errors', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            status: 400,
            title: 'Bad Request',
            detail: 'Validation failed',
            errors: [{ field: 'candidateRefs[0]', message: 'candidateRef already invited: dup', code: 'DUPLICATE' }],
          }),
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Enter a candidate reference.')
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="invite-single-input"]').setValue('dup')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('candidateRef already invited: dup')
  })

  it('clears generated links when switching to another configId', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        jsonResponse(
          [{ inviteId: 'inv-1', candidateRef: 'alice', link: 'http://localhost:5173/invite/tok-a' }],
          201,
        ),
      )
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('[data-testid="invite-single-input"]').setValue('alice')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('http://localhost:5173/invite/tok-a')

    await wrapper.setProps({ configId: '22222222-2222-2222-2222-222222222222' })
    await flushPromises()
    expect(wrapper.text()).not.toContain('http://localhost:5173/invite/tok-a')
  })
})

describe('invite CSV helpers', () => {
  it('parses quoted commas, escaped quotes, UTF-8 BOM, CRLF, and headers after leading blank lines', () => {
    const csv = '\uFEFF\r\n\r\ncandidate_ref,cohort\r\n"Doe, Jane",CS101\r\n"O""Brien, Pat",CS101\r\n  cand_3 ,extra\r\n'
    expect(parseCandidateCsv(csv)).toEqual(['Doe, Jane', 'O"Brien, Pat', 'cand_3'])
  })

  it('neutralizes spreadsheet formula prefixes and quotes cells in exported CSV', () => {
    const csv = formatInvitesCsv([
      { inviteId: '1', candidateRef: '=SUM(A1)', link: 'http://localhost:5173/invite/t1' },
      { inviteId: '2', candidateRef: 'Doe, "Jane"', link: 'http://localhost:5173/invite/t2' },
    ])
    expect(csv).toBe(
      'candidate_ref,link\n"\'=SUM(A1)","http://localhost:5173/invite/t1"\n"Doe, ""Jane""","http://localhost:5173/invite/t2"\n',
    )
  })
})
