import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type GlobalMountOptions } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import ResultsView from '@/views/assessment/ResultsView.vue'
import EvidenceDialog from './EvidenceDialog.vue'
import SkillsMatrixPanel from './SkillsMatrixPanel.vue'

// REQ-SM-11, REQ-CR-07: live status polling and the Release gate.
const jsonResponse = (body: unknown) =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })

const dialogGlobal = { stubs: { teleport: true, transition: false } }

function plugins(): NonNullable<GlobalMountOptions['plugins']> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return [pinia, PrimeVue, [VueQueryPlugin, { queryClient }]]
}

const matrixBody = (evaluationStatus: string) => ({
  configId: 'cfg-1',
  formulaVersionId: null,
  formulaVersionNumber: 1,
  formulaPreset: 'WEIGHTED_MEAN',
  minCoverage: 0.7,
  skills: [{ skillId: 's1', skillName: 'Spring Boot' }],
  rows: [
    {
      inviteId: 'inv-1',
      candidateRef: 'anna.schmidt',
      attemptId: 'att-1',
      attemptNo: 1,
      evaluationStatus,
      fitScore: null,
      coverage: 0,
      releaseStatus: 'pending',
      flaggedCount: 0,
      releasedAt: null,
      cells: [],
    },
  ],
})

const drilldownBody = (evaluationStatus: string, flaggedCount = 0, releaseStatus = 'pending') => ({
  attemptId: 'att-1',
  configId: 'cfg-1',
  inviteId: 'inv-1',
  candidateRef: 'anna.schmidt',
  attemptNo: 1,
  evaluationStatus,
  fitScore: null,
  coverage: 0,
  minCoverage: 0.7,
  releaseStatus,
  flaggedCount,
  releasedAt: null,
  skillScores: [],
  responses: [],
})

const callsTo = (fetchMock: ReturnType<typeof vi.fn>, part: string) =>
  fetchMock.mock.calls.filter(([url]) => String(url).includes(part)).length

describe('Live status and Release gate (REQ-SM-11, REQ-CR-07)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('the matrix refetches every 5 s while a row is RUNNING and stops when all rows are COMPLETED', async () => {
    let served = 0
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(jsonResponse(matrixBody(served++ === 0 ? 'RUNNING' : 'COMPLETED'))),
    )
    vi.stubGlobal('fetch', fetchMock)
    mount(SkillsMatrixPanel, { props: { configId: 'cfg-1' }, global: { plugins: [...plugins()] } })
    await vi.advanceTimersByTimeAsync(0)
    expect(callsTo(fetchMock, '/matrix')).toBe(1)

    await vi.advanceTimersByTimeAsync(5000)
    expect(callsTo(fetchMock, '/matrix')).toBe(2)

    await vi.advanceTimersByTimeAsync(20000)
    expect(callsTo(fetchMock, '/matrix')).toBe(2)
  })

  it('the Refresh button refetches the matrix once', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(matrixBody('COMPLETED'))))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(SkillsMatrixPanel, { props: { configId: 'cfg-1' }, global: { plugins: [...plugins()] } })
    await vi.advanceTimersByTimeAsync(0)

    await wrapper.get('[data-testid="matrix-refresh-btn"]').trigger('click')
    await vi.advanceTimersByTimeAsync(0)

    expect(callsTo(fetchMock, '/matrix')).toBe(2)
  })

  it.each([
    ['QUEUED', 0, 'pending', 'still running'],
    ['RUNNING', 0, 'pending', 'still running'],
    ['FAILED', 0, 'pending', 'failed'],
    ['COMPLETED', 1, 'flagged', 'flagged'],
    ['COMPLETED', 0, 'approved', 'Already released'],
  ])('Release is disabled for %s with %i flagged and %s, and the tooltip names the reason', async (status, flagged, release, reason) => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(drilldownBody(status, flagged, release)))),
    )
    const wrapper = mount(EvidenceDialog, {
      props: { attemptId: 'att-1', visible: true },
      global: { ...dialogGlobal, plugins: [...plugins()] },
    })
    await vi.advanceTimersByTimeAsync(0)

    expect(wrapper.get('[data-testid="release-attempt-btn"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="release-attempt-tip"]').attributes('title')).toContain(reason)
  })

  it('Release is enabled for a COMPLETED evaluation with no flags and no earlier release', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(drilldownBody('COMPLETED')))),
    )
    const wrapper = mount(EvidenceDialog, {
      props: { attemptId: 'att-1', visible: true },
      global: { ...dialogGlobal, plugins: [...plugins()] },
    })
    await vi.advanceTimersByTimeAsync(0)

    expect(wrapper.get('[data-testid="release-attempt-btn"]').attributes('disabled')).toBeUndefined()
  })

  it('the evidence dialog refetches the drilldown every 5 s while the evaluation is RUNNING', async () => {
    let served = 0
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(jsonResponse(drilldownBody(served++ === 0 ? 'RUNNING' : 'COMPLETED'))),
    )
    vi.stubGlobal('fetch', fetchMock)
    mount(EvidenceDialog, {
      props: { attemptId: 'att-1', visible: true },
      global: { ...dialogGlobal, plugins: [...plugins()] },
    })
    await vi.advanceTimersByTimeAsync(0)
    expect(callsTo(fetchMock, '/evaluations')).toBe(1)

    await vi.advanceTimersByTimeAsync(5000)
    expect(callsTo(fetchMock, '/evaluations')).toBe(2)

    await vi.advanceTimersByTimeAsync(20000)
    expect(callsTo(fetchMock, '/evaluations')).toBe(2)
  })

  it('the candidate results page refetches every 5 s while released is false, then stops', async () => {
    let served = 0
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(
        jsonResponse(
          served++ === 0
            ? { configId: 'cfg-1', releaseStatus: 'pending', released: false }
            : { configId: 'cfg-1', releaseStatus: 'approved', released: true, skillScores: [], evidence: [] },
        ),
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/assessment/results/:configId', component: ResultsView }],
    })
    await router.push('/assessment/results/cfg-1')
    await router.isReady()
    const wrapper = mount(ResultsView, { global: { plugins: [router, ...plugins()] } })
    await vi.advanceTimersByTimeAsync(0)
    expect(wrapper.find('[data-testid="results-pending"]').exists()).toBe(true)

    await vi.advanceTimersByTimeAsync(5000)
    expect(wrapper.find('[data-testid="results-released"]').exists()).toBe(true)

    await vi.advanceTimersByTimeAsync(20000)
    expect(callsTo(fetchMock, '/results/')).toBe(2)
    await flushPromises()
  })
})

// REQ-EP-12: the professor re-runs a FAILED evaluation from the Evidence dialog.
describe('Re-run evaluation (REQ-EP-12)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows Re-run evaluation only for FAILED, posts the retry and refetches the drilldown', async () => {
    let status = 'FAILED'
    const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (String(url).includes('/evaluation/retry') && init?.method === 'POST') {
        status = 'QUEUED'
        return Promise.resolve(jsonResponse({ attemptId: 'att-1', evaluationStatus: 'QUEUED' }))
      }
      return Promise.resolve(jsonResponse(drilldownBody(status)))
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(EvidenceDialog, {
      props: { attemptId: 'att-1', visible: true },
      global: { ...dialogGlobal, plugins: [...plugins()] },
    })
    await flushPromises()

    await wrapper.get('[data-testid="rerun-evaluation-btn"]').trigger('click')
    await flushPromises()

    const retryCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/attempts/att-1/evaluation/retry'))
    expect(retryCall?.[1]?.method).toBe('POST')
    expect(wrapper.find('[data-testid="rerun-evaluation-btn"]').exists()).toBe(false)
  })

  it.each(['QUEUED', 'RUNNING', 'COMPLETED'])('hides Re-run evaluation for %s', async (status) => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(drilldownBody(status)))))
    const wrapper = mount(EvidenceDialog, {
      props: { attemptId: 'att-1', visible: true },
      global: { ...dialogGlobal, plugins: [...plugins()] },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="rerun-evaluation-btn"]').exists()).toBe(false)
  })
})
