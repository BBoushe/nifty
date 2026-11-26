import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { TeamProposal } from '@/api/teams'
import TeamBuilderView from './TeamBuilderView.vue'

const CONFIG_ID = '11111111-1111-1111-1111-111111111111'
const PROPOSAL_ID = '22222222-2222-2222-2222-222222222222'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const member = (id: string, ref: string, docker: number | null) => ({
  inviteId: id,
  candidateRef: ref,
  skillScores: { git: 3.5, docker },
  traits: { extraversion: 2.5, neuroticism: 4 },
})

function proposal(overrides: Partial<TeamProposal> = {}): TeamProposal {
  return {
    proposalId: PROPOSAL_ID,
    configId: CONFIG_ID,
    status: 'DRAFT',
    numTeams: 2,
    groupingMode: 'COMBINED',
    alpha: 0.5,
    combinedHardWeight: 0.6,
    seed: 42,
    createdAt: '2026-11-26T10:00:00Z',
    publishedAt: null,
    diagnostics: { imbalance: 0.1234, diversity: 0.4321, cost: -0.1, naCount: 1 },
    teams: [
      {
        teamNo: 1,
        size: 2,
        members: [member('inv-a', 'cand_a', null), member('inv-b', 'cand_b', 2)],
        skillAverages: { git: 3.5, docker: 2 },
        skillNaCounts: { git: 0, docker: 1 },
        traitAverages: { extraversion: 2.5, neuroticism: 4 },
      },
      {
        teamNo: 2,
        size: 1,
        members: [member('inv-c', 'cand_c', 3)],
        skillAverages: { git: 3.5, docker: 3 },
        skillNaCounts: { git: 0, docker: 0 },
        traitAverages: { extraversion: 2.5, neuroticism: 4 },
      },
    ],
    pendingInviteIds: ['inv-wait'],
    ...overrides,
  }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/ranking/teams/:configId', component: TeamBuilderView }],
  })
  await router.push(`/ranking/teams/${CONFIG_ID}`)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(TeamBuilderView, {
    global: { plugins: [router, [VueQueryPlugin, { queryClient }], PrimeVue] },
  })
  await flushPromises()
  return wrapper
}

describe('TeamBuilderView (REQ-TB-03, REQ-TB-04, REQ-TB-07, REQ-TB-11)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows per-skill averages, N/A counts, diagnostics, traits and waiting candidates from the backend', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(jsonResponse([proposal()])))
    const wrapper = await mountView()

    const team1 = wrapper.get('[data-testid="team-1"]')
    expect(team1.text()).toContain('cand_a')
    expect(team1.get('[data-testid="avg-git"]').text()).toContain('3.5')
    expect(team1.get('[data-testid="avg-docker"]').text()).toContain('2')
    expect(team1.get('[data-testid="na-docker"]').text()).toContain('1')
    expect(team1.get('[data-testid="trait-neuroticism"]').text()).toContain('4')
    // N/A member score is a hatched N/A, never 0.
    const naScore = team1.get('[data-testid="member-inv-a"] [data-testid="score-docker"]')
    expect(naScore.text()).toContain('N/A')
    expect(naScore.text()).not.toContain('0')
    expect(wrapper.get('[data-testid="diag-imbalance"]').text()).toContain('0.1234')
    expect(wrapper.get('[data-testid="diag-diversity"]').text()).toContain('0.4321')
    expect(wrapper.get('[data-testid="diag-na"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="pending"]').text()).toContain('1')
    expect(wrapper.find('[data-testid="chart"]').exists()).toBe(true)
  })

  it('drops a member on another team, posts the full placement to /recalculate and updates the cards live', async () => {
    const moved = proposal({
      teams: [
        { ...proposal().teams[0], size: 1, members: [member('inv-b', 'cand_b', 2)] },
        { ...proposal().teams[1], size: 2, members: [member('inv-c', 'cand_c', 3), member('inv-a', 'cand_a', null)] },
      ],
      diagnostics: { imbalance: 0.5, diversity: 0.25, cost: 0.1, naCount: 1 },
    })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([proposal()]))
      .mockResolvedValueOnce(jsonResponse(moved))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = await mountView()

    await wrapper.get('[data-testid="member-inv-a"]').trigger('dragstart')
    await wrapper.get('[data-testid="team-2"]').trigger('drop')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(
      `/api/v1/ranking/teams/${PROPOSAL_ID}/recalculate`,
      expect.objectContaining({ method: 'POST' }),
    )
    const body = JSON.parse(fetchMock.mock.calls[1][1].body)
    expect(body.assignments).toEqual(
      expect.arrayContaining([
        { inviteId: 'inv-a', teamNo: 2 },
        { inviteId: 'inv-b', teamNo: 1 },
        { inviteId: 'inv-c', teamNo: 2 },
      ]),
    )
    expect(body.assignments).toHaveLength(3)
    expect(wrapper.get('[data-testid="team-2"]').text()).toContain('cand_a')
    expect(wrapper.get('[data-testid="team-1"]').text()).not.toContain('cand_a')
    expect(wrapper.get('[data-testid="diag-imbalance"]').text()).toContain('0.5')
  })

  it('moves a member with the keyboard-accessible select as well', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([proposal()]))
      .mockResolvedValueOnce(jsonResponse(proposal()))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = await mountView()

    await wrapper.get('[data-testid="move-inv-a"]').setValue('2')
    await flushPromises()

    const body = JSON.parse(fetchMock.mock.calls[1][1].body)
    expect(body.assignments).toContainEqual({ inviteId: 'inv-a', teamNo: 2 })
  })

  it('publishes the proposal and locks dragging, moving and publishing', async () => {
    const published = proposal({ status: 'PUBLISHED', publishedAt: '2026-11-26T11:00:00Z' })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([proposal()]))
      .mockResolvedValueOnce(jsonResponse(published))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="member-inv-a"]').attributes('draggable')).toBe('true')

    await wrapper.get('[data-testid="publish"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(
      `/api/v1/ranking/teams/${PROPOSAL_ID}/publish`,
      expect.objectContaining({ method: 'POST' }),
    )
    expect(wrapper.find('[data-testid="publish"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="status"]').text()).toContain('PUBLISHED')
    expect(wrapper.get('[data-testid="member-inv-a"]').attributes('draggable')).toBe('false')
    expect(wrapper.get('[data-testid="move-inv-a"]').attributes('disabled')).toBeDefined()
  })

  it('shows the empty state and generates a proposal', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(proposal(), 201))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('No team proposal yet')

    await wrapper.get('[data-testid="generate"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(
      `/api/v1/ranking/configs/${CONFIG_ID}/teams/generate`,
      expect.objectContaining({ method: 'POST' }),
    )
    expect(wrapper.find('[data-testid="team-1"]').exists()).toBe(true)
  })

  it('shows the backend error message when loading fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify({ status: 404, detail: 'Assessment not found.' }), {
          status: 404,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    )
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('Assessment not found.')
  })
})
