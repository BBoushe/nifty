import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import EvidenceDialog from './EvidenceDialog.vue'
import RankingView from './RankingView.vue'
import SkillsMatrixPanel from './SkillsMatrixPanel.vue'

// REQ-SM-12 (candidate names) and REQ-NAV-11 (Ranking links).
const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

const assessments = [
  { configId: 'cfg-a', title: 'Assessment A', version: 1 },
  { configId: 'cfg-b', title: 'Assessment B', version: 1 },
]

const matrixBody = {
  configId: 'cfg-a',
  formulaVersionId: null,
  formulaVersionNumber: 1,
  formulaPreset: 'WEIGHTED_MEAN',
  minCoverage: 0.7,
  skills: [{ skillId: 's1', skillName: 'Spring Boot' }],
  rows: [
    {
      inviteId: 'inv-12345678-aaaa',
      candidateRef: 'anna.schmidt',
      attemptId: 'att-1',
      attemptNo: 1,
      evaluationStatus: 'COMPLETED',
      fitScore: 3,
      coverage: 1,
      releaseStatus: 'approved',
      flaggedCount: 0,
      releasedAt: null,
      cells: [
        {
          skillId: 's1',
          skillName: 'Spring Boot',
          status: 'SCORED',
          value: 3,
          avgConfidence: 0.9,
          verifiedEvidenceCount: 2,
          reviewStatus: 'approved',
        },
      ],
    },
  ],
}

function fetchRouter(url: string): Promise<Response> {
  if (url.endsWith('/studio/configs')) return Promise.resolve(jsonResponse(assessments))
  if (url.includes('/matrix')) return Promise.resolve(jsonResponse(matrixBody))
  if (url.includes('/formula/preview')) {
    return Promise.resolve(
      jsonResponse({
        configId: 'cfg-a',
        preset: 'WEIGHTED_MEAN',
        maxEntrySkillWeightShare: 0.5,
        candidates: [
          { inviteId: 'inv-12345678-aaaa', candidateRef: 'anna.schmidt', attemptId: 'att-1', fitScore: 3, coverage: 1, skillScores: [] },
        ],
      }),
    )
  }
  if (url.includes('/attempts/att-1/evaluations')) {
    return Promise.resolve(
      jsonResponse({
        attemptId: 'att-1',
        configId: 'cfg-a',
        inviteId: 'inv-12345678-aaaa',
        candidateRef: 'anna.schmidt',
        attemptNo: 1,
        evaluationStatus: 'COMPLETED',
        fitScore: 3,
        coverage: 1,
        minCoverage: 0.7,
        releaseStatus: 'approved',
        flaggedCount: 0,
        releasedAt: null,
        skillScores: [],
        responses: [],
      }),
    )
  }
  return Promise.resolve(jsonResponse({}))
}

function newQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

async function mountRanking(path: string, queryClient = newQueryClient()) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/ranking', name: 'ranking', component: RankingView },
      { path: '/ranking/configs/:configId', name: 'ranking-config', component: RankingView },
      { path: '/ranking/teams/:configId', name: 'team-builder', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(RankingView, {
    global: {
      plugins: [router, PrimeVue, [VueQueryPlugin, { queryClient }]],
      stubs: { teleport: true, transition: false },
    },
  })
  await flushPromises()
  return { wrapper, router, queryClient }
}

describe('Candidate names (REQ-SM-12)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('the matrix table and the heatmap label a row with candidateRef and attempt number', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(fetchRouter))
    const wrapper = mount(SkillsMatrixPanel, {
      props: { configId: 'cfg-a' },
      global: { plugins: [PrimeVue, [VueQueryPlugin, { queryClient: newQueryClient() }]] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('anna.schmidt')
    expect(wrapper.text()).toContain('(#1)')
    expect(wrapper.text()).not.toContain('inv-1234')
    expect(wrapper.get('[data-testid="chart"]').attributes('data-option')).toContain('anna.schmidt (#1)')
  })

  it('the evidence dialog header shows candidateRef', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(fetchRouter))
    const wrapper = mount(EvidenceDialog, {
      props: { attemptId: 'att-1', visible: true },
      global: {
        plugins: [PrimeVue, [VueQueryPlugin, { queryClient: newQueryClient() }]],
        stubs: { teleport: true, transition: false },
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="evidence-dialog"]').text()).toContain('anna.schmidt')
    expect(wrapper.text()).not.toContain('inv-1234')
  })

  it('the formula preview lists candidateRef', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(fetchRouter))
    const { wrapper } = await mountRanking('/ranking/configs/cfg-a')

    const tabs = wrapper.findAll('button').filter((b) => b.text() === 'Formula Presets')
    await tabs[0].trigger('click')
    const preview = wrapper.findAll('button').filter((b) => b.text() === 'Preview What-If')
    await preview[0].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('anna.schmidt: fit 3.00')
  })
})

describe('Ranking route sync and links (REQ-NAV-11)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('a route param change from A to B selects assessment B', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(fetchRouter))
    const { wrapper, router } = await mountRanking('/ranking/configs/cfg-a')
    const select = wrapper.get('[data-testid="ranking-config-select"]').element as HTMLSelectElement
    expect(select.value).toBe('cfg-a')

    await router.push('/ranking/configs/cfg-b')
    await flushPromises()

    expect(select.value).toBe('cfg-b')
  })

  it('a dropdown change replaces the URL with the selected assessment', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(fetchRouter))
    const { wrapper, router } = await mountRanking('/ranking/configs/cfg-a')
    const replace = vi.spyOn(router, 'replace')

    await wrapper.get('[data-testid="ranking-config-select"]').setValue('cfg-b')
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({ name: 'ranking-config', params: { configId: 'cfg-b' } })
    expect(router.currentRoute.value.fullPath).toBe('/ranking/configs/cfg-b')
  })

  it('the Build teams link points to the Team Builder of the selected assessment', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(fetchRouter))
    const { wrapper } = await mountRanking('/ranking/configs/cfg-a')

    expect(wrapper.get('[data-testid="build-teams-link"]').attributes('href')).toBe('/ranking/teams/cfg-a')
  })

  it('Ranking reads the assessment list under the key that Studio invalidates on delete', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(fetchRouter))
    const { queryClient } = await mountRanking('/ranking/configs/cfg-a')

    const state = queryClient.getQueryState(['studio', 'assessments'])
    expect(state?.status).toBe('success')
    await queryClient.invalidateQueries({ queryKey: ['studio', 'assessments'] })
    const listCalls = (fetch as ReturnType<typeof vi.fn>).mock.calls.filter(([u]) => String(u).endsWith('/studio/configs'))
    expect(listCalls.length).toBe(2)
  })
})
