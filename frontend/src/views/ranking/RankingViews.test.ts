import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import TranscriptDialog from '@/components/TranscriptDialog.vue'
import CohortGapsPanel from './CohortGapsPanel.vue'
import EvidenceDialog from './EvidenceDialog.vue'
import RankingView from './RankingView.vue'
import SkillsMatrixPanel from './SkillsMatrixPanel.vue'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

// The Dialog portal mounts one tick late, and teleport is stubbed so the tests can query it.
// Transitions run for real: PrimeVue binds its Esc listener in the enter hook.
const dialogGlobal = { stubs: { teleport: true, transition: false } }
// Leave transitions need a few frames before the Dialog leaves the DOM.
const settle = () => new Promise((resolve) => setTimeout(resolve, 200))

const quote = 'wrap the write in @Transactional'
const answerText = `I ${quote} so failures roll back.`

// One attempt (att-1) with one flagged evaluation and one transcript entry, shared by the modal tests.
function attemptFetch(url: string): Promise<Response> {
  if (url.includes('/attempts/att-1/evaluations')) {
    return Promise.resolve(
      jsonResponse({
        attemptId: 'att-1',
        configId: 'cfg-1',
        inviteId: 'inv-12345678',
        attemptNo: 1,
        fitScore: 2.0,
        coverage: 1.0,
        minCoverage: 0.7,
        releaseStatus: 'flagged',
        flaggedCount: 1,
        releasedAt: null,
        skillScores: [],
        responses: [
          {
            responseEvaluationId: 're-1',
            entryId: 'e-1',
            coreQuestionId: 'q-1',
            skillId: 's1',
            skillName: 'Spring Boot',
            questionOrigin: 'core',
            questionWeight: 2.0,
            confidenceThreshold: 0.7,
            run1Level: 2,
            run2Level: 3,
            run1Quote: quote,
            run2Quote: quote,
            quoteVerified: true,
            verifiedQuote: quote,
            aiRationale: 'Understands transaction boundaries.',
            aiLevel: 2,
            calculatedConfidence: 0.62,
            flagReasons: ['LOW_CONFIDENCE'],
            reviewStatus: 'flagged',
            effectiveLevel: 2,
            overriddenBy: null,
            overrideReason: null,
            reviewedAt: null,
          },
        ],
      }),
    )
  }
  if (url.includes('/transcripts/att-1')) {
    return Promise.resolve(
      jsonResponse({
        format: 'assessment-transcript/v1',
        transcriptId: 'att-1',
        candidateId: 'inv-12345678',
        configId: 'cfg-1',
        configVersion: 'v1',
        attempt: 1,
        startedAt: '2026-11-26T11:50:00Z',
        entries: [
          {
            entryId: 'e-1',
            turn: 1,
            question: {
              text: 'Explain @Transactional boundaries.',
              origin: 'core',
              coreQuestionId: 'q-1',
              weight: 2.0,
              targetSkills: ['s1'],
              askedAt: '2026-11-26T11:50:05Z',
              expiresAt: null,
            },
            answer: {
              text: answerText,
              submittedAt: '2026-11-26T11:50:47Z',
              durationSec: 42,
              timedOut: true,
              pasteSuspected: true,
              injectionSuspected: true,
            },
            metadata: { promptedTerms: [], unpromptedTerms: [] },
          },
        ],
        evidence: [],
      }),
    )
  }
  return Promise.resolve(jsonResponse({}))
}

async function mountEvidence() {
  const { pinia, queryClient } = createPlugins()
  const wrapper = mount(EvidenceDialog, {
    props: {
      attemptId: 'att-1',
      visible: true,
      'onUpdate:visible': (v: boolean) => wrapper.setProps({ visible: v }),
    },
    global: { ...dialogGlobal, plugins: [pinia, PrimeVue, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

function createPlugins() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return { pinia, queryClient }
}

describe('Ranking Views (US-EV-06, REQ-SM-01..08)', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('SkillsMatrixPanel renders hatched N/A, flagged red border, separate fit/coverage, and no bulk-approve button (Decision 2.30)', async () => {
    const configId = '11111111-1111-1111-1111-111111111111'
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/matrix')) {
        return Promise.resolve(
          jsonResponse({
            configId,
            formulaVersionId: '22222222-2222-2222-2222-222222222222',
            formulaVersionNumber: 1,
            formulaPreset: 'WEIGHTED_MEAN',
            minCoverage: 0.7,
            skills: [
              { skillId: 's1', skillName: 'Spring Boot' },
              { skillId: 's2', skillName: 'Docker' },
            ],
            rows: [
              {
                inviteId: 'inv-1',
                attemptId: 'att-1',
                attemptNo: 1,
                evaluationStatus: 'COMPLETED',
                fitScore: 3.25,
                coverage: 0.8,
                releaseStatus: 'flagged',
                flaggedCount: 1,
                releasedAt: null,
                cells: [
                  {
                    skillId: 's1',
                    skillName: 'Spring Boot',
                    status: 'SCORED',
                    value: 3.25,
                    avgConfidence: 0.52,
                    verifiedEvidenceCount: 2,
                    reviewStatus: 'flagged',
                  },
                  {
                    skillId: 's2',
                    skillName: 'Docker',
                    status: 'N/A',
                    value: null,
                    avgConfidence: null,
                    verifiedEvidenceCount: 0,
                    reviewStatus: 'pending',
                  },
                ],
              },
              {
                // Waiting retake: no scores yet, shown as evaluating (REQ-SM-07).
                inviteId: 'inv-2',
                attemptId: 'att-2',
                attemptNo: 2,
                evaluationStatus: 'RUNNING',
                fitScore: null,
                coverage: 0,
                releaseStatus: 'pending',
                flaggedCount: 0,
                releasedAt: null,
                cells: [
                  {
                    skillId: 's1',
                    skillName: 'Spring Boot',
                    status: 'N/A',
                    value: null,
                    avgConfidence: null,
                    verifiedEvidenceCount: 0,
                    reviewStatus: 'pending',
                  },
                  {
                    skillId: 's2',
                    skillName: 'Docker',
                    status: 'N/A',
                    value: null,
                    avgConfidence: null,
                    verifiedEvidenceCount: 0,
                    reviewStatus: 'pending',
                  },
                ],
              },
            ],
          }),
        )
      }
      return Promise.resolve(jsonResponse({}))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { pinia, queryClient } = createPlugins()
    const wrapper = mount(SkillsMatrixPanel, {
      props: { configId },
      global: { plugins: [pinia, PrimeVue, [VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()

    // ECharts heatmap renders with mock stub
    expect(wrapper.find('[data-testid="chart"]').exists()).toBe(true)
    // Hatched N/A cell rendered explicitly, never as 0
    const naCell = wrapper.get('[data-testid="matrix-cell-att-1-s2"]')
    expect(naCell.classes()).toContain('score-hatch')
    expect(naCell.text()).toContain('N/A')
    // Flagged cell highlighted with danger border
    const flaggedCell = wrapper.get('[data-testid="matrix-cell-att-1-s1"]')
    expect(flaggedCell.classes()).toContain('border-danger')
    // Fit and coverage displayed separately from backend values
    expect(wrapper.text()).toContain('3.25')
    expect(wrapper.text()).toContain('80%')
    // A waiting attempt shows as evaluating with N/A cells, never as a score
    expect(wrapper.get('[data-testid="evaluation-status-att-2"]').text()).toContain('evaluating')
    expect(wrapper.get('[data-testid="matrix-cell-att-2-s1"]').text()).toContain('N/A')

    // Decision 2.30 / REQ-SM-03: no bulk approval action in professor UI
    expect(wrapper.find('[data-testid="bulk-approve-btn"]').exists()).toBe(false)
  })

  it('EvidenceDialog displays verified quote, requires override reason, submits override, and blocks release while flagged', async () => {
    const attemptId = 'att-1'
    const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes('/attempts/att-1/evaluations')) {
        return Promise.resolve(
          jsonResponse({
            attemptId,
            configId: 'cfg-1',
            inviteId: 'inv-1',
            attemptNo: 1,
            fitScore: 2.0,
            coverage: 1.0,
            minCoverage: 0.7,
            releaseStatus: 'flagged',
            flaggedCount: 1,
            releasedAt: null,
            skillScores: [],
            responses: [
              {
                responseEvaluationId: 're-1',
                entryId: 'e-1',
                coreQuestionId: 'q-1',
                skillId: 's1',
                skillName: 'Spring Boot',
                questionOrigin: 'core',
                questionWeight: 2.0,
                confidenceThreshold: 0.7,
                run1Level: 2,
                run2Level: 3,
                run1Quote: 'wrap the write in @Transactional',
                run2Quote: 'wrap the write in @Transactional',
                quoteVerified: true,
                verifiedQuote: 'wrap the write in @Transactional',
                aiRationale: 'Understands transaction boundaries.',
                aiLevel: 2,
                calculatedConfidence: 0.62,
                flagReasons: ['JUDGE_DISAGREEMENT', 'LOW_CONFIDENCE'],
                reviewStatus: 'flagged',
                effectiveLevel: 2,
                overriddenBy: null,
                overrideReason: null,
                reviewedAt: null,
              },
            ],
          }),
        )
      }
      if (url.includes('/evaluations/re-1/approve') && init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse({
            responseEvaluationId: 're-1',
            attemptId,
            effectiveLevel: 2,
            reviewStatus: 'approved',
            reviewedAt: '2026-11-26T12:00:00Z',
            remainingFlaggedCount: 0,
          }),
        )
      }
      if (url.includes('/transcripts/att-1')) {
        return Promise.resolve(
          jsonResponse({
            format: 'assessment-transcript/v1',
            transcriptId: attemptId,
            candidateId: 'inv-1',
            configId: 'cfg-1',
            configVersion: 'v1',
            attempt: 1,
            startedAt: '2026-11-26T11:50:00Z',
            entries: [
              {
                entryId: 'e-1',
                turn: 1,
                question: {
                  text: 'Explain @Transactional boundaries.',
                  origin: 'core',
                  coreQuestionId: 'q-1',
                  weight: 2.0,
                  targetSkills: ['s1'],
                  askedAt: '2026-11-26T11:50:05Z',
                  expiresAt: '2026-11-26T11:55:05Z',
                },
                answer: {
                  text: 'I wrap the write in @Transactional so failures roll back.',
                  submittedAt: '2026-11-26T11:50:47Z',
                  durationSec: 42,
                  timedOut: true,
                  pasteSuspected: true,
                  injectionSuspected: true,
                },
                metadata: { promptedTerms: [], unpromptedTerms: [] },
              },
            ],
            evidence: [],
          }),
        )
      }
      if (url.includes('/evaluations/re-1/override') && init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse({
            responseEvaluationId: 're-1',
            attemptId,
            aiLevel: 2,
            effectiveLevel: 3,
            reviewStatus: 'overridden',
            overrideReason: 'Demonstrated practical mastery',
            reviewedAt: '2026-11-26T12:00:00Z',
            updatedFitScore: 3.0,
            updatedCoverage: 1.0,
            remainingFlaggedCount: 0,
          }),
        )
      }
      return Promise.resolve(jsonResponse({}))
    })
    vi.stubGlobal('fetch', fetchMock)

    const { pinia, queryClient } = createPlugins()
    const wrapper = mount(EvidenceDialog, {
      props: { attemptId, visible: true },
      global: { ...dialogGlobal, plugins: [pinia, PrimeVue, [VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('wrap the write in @Transactional')
    expect(wrapper.text()).toContain('JUDGE_DISAGREEMENT')
    expect(wrapper.get('[data-testid="release-attempt-btn"]').attributes('disabled')).toBeDefined()

    // Professor can approve a flagged item as-is without fabricating an override
    await wrapper.get('[data-testid="approve-eval-re-1"]').trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/ranking/evaluations/re-1/approve'),
      expect.objectContaining({ method: 'POST' }),
    )

    await wrapper.get('[data-testid="override-level-re-1"]').setValue('3')
    await wrapper.get('[data-testid="override-reason-re-1"]').setValue('Demonstrated practical mastery')
    await wrapper.get('[data-testid="override-submit-re-1"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/ranking/evaluations/re-1/override'),
      expect.objectContaining({ method: 'POST' }),
    )
    // REQ-SM-02 / REQ-SM-08: override invalidates transcript query so export/ledger stay fresh
    const transcriptCalls = fetchMock.mock.calls.filter(([url]) =>
      String(url).includes('/transcripts/att-1'),
    )
    expect(transcriptCalls.length).toBeGreaterThanOrEqual(2)
  })

  it('EvidenceDialog disables Release Attempt when attempt is already auto_released (Decision 2.10)', async () => {
    const attemptId = 'att-released'
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/attempts/att-released/evaluations')) {
          return Promise.resolve(
            jsonResponse({
              attemptId,
              configId: 'cfg-1',
              inviteId: 'inv-1',
              attemptNo: 1,
              fitScore: 3.5,
              coverage: 1.0,
              minCoverage: 0.7,
              releaseStatus: 'auto_released',
              flaggedCount: 0,
              releasedAt: '2026-11-26T12:00:00Z',
              skillScores: [],
              responses: [],
            }),
          )
        }
        if (url.includes('/transcripts/att-released')) {
          return Promise.resolve(
            jsonResponse({
              format: 'assessment-transcript/v1',
              transcriptId: attemptId,
              candidateId: 'inv-1',
              configId: 'cfg-1',
              configVersion: 'v1',
              attempt: 1,
              startedAt: '2026-11-26T11:50:00Z',
              entries: [],
              evidence: [],
            }),
          )
        }
        return Promise.resolve(jsonResponse({}))
      }),
    )

    const { pinia, queryClient } = createPlugins()
    const wrapper = mount(EvidenceDialog, {
      props: { attemptId, visible: true },
      global: { ...dialogGlobal, plugins: [pinia, PrimeVue, [VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="release-attempt-btn"]').attributes('disabled')).toBeDefined()
  })

  it('EvidenceDialog has no inline ledger; View transcript opens the chat dialog with markers (REQ-SM-10)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(attemptFetch))
    const wrapper = await mountEvidence()

    expect(wrapper.find('[data-testid="conversation-ledger"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="transcript-dialog"]').exists()).toBe(false)

    await wrapper.get('[data-testid="view-transcript-btn"]').trigger('click')
    await flushPromises()

    const transcript = wrapper.get('[data-testid="transcript-dialog"]')
    expect(transcript.text()).toContain('Core question 1')
    expect(transcript.text()).toContain('Explain @Transactional boundaries.')
    expect(transcript.text()).toContain(answerText)
    expect(transcript.text()).toContain('answered in 42 s')
    expect(transcript.find('[data-testid="marker-timed-out-e-1"]').exists()).toBe(true)
    expect(transcript.find('[data-testid="marker-paste-e-1"]').exists()).toBe(true)
    expect(transcript.find('[data-testid="marker-injection-e-1"]').exists()).toBe(true)
    expect(wrapper.findComponent(TranscriptDialog).props('showMarkers')).toBe(true)
  })

  it('Show in transcript passes the focused entry and highlights the verified quote (REQ-SM-10)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(attemptFetch))
    const wrapper = await mountEvidence()

    await wrapper.get('[data-testid="show-in-transcript-re-1"]').trigger('click')
    await flushPromises()

    const transcript = wrapper.findComponent(TranscriptDialog)
    expect(transcript.props('focusEntryId')).toBe('e-1')
    expect(transcript.props('highlight')).toEqual({ 'e-1': [quote] })
    expect(wrapper.get('[data-testid="answer-e-1"] mark').text()).toBe(quote)
  })

  it('Close and Esc close the evidence dialog; Esc with the transcript open closes only the transcript (REQ-SM-09)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(attemptFetch))
    const wrapper = await mountEvidence()
    const esc = () => document.dispatchEvent(new KeyboardEvent('keydown', { code: 'Escape' }))

    await wrapper.get('[data-testid="view-transcript-btn"]').trigger('click')
    await flushPromises()
    esc()
    await settle()
    expect(wrapper.find('[data-testid="transcript-dialog"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="evidence-dialog"]').exists()).toBe(true)

    esc()
    await settle()
    expect(wrapper.find('[data-testid="evidence-dialog"]').exists()).toBe(false)
  })

  it('Inspect opens a modal dialog while the matrix stays mounted (REQ-SM-09)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/matrix')) {
          return Promise.resolve(
            jsonResponse({
              configId: 'cfg-1',
              formulaVersionId: null,
              formulaVersionNumber: 1,
              formulaPreset: 'WEIGHTED_MEAN',
              minCoverage: 0.7,
              skills: [{ skillId: 's1', skillName: 'Spring Boot' }],
              rows: [
                {
                  inviteId: 'inv-12345678',
                  attemptId: 'att-1',
                  attemptNo: 1,
                  evaluationStatus: 'COMPLETED',
                  fitScore: 2.0,
                  coverage: 1.0,
                  releaseStatus: 'flagged',
                  flaggedCount: 1,
                  releasedAt: null,
                  cells: [
                    {
                      skillId: 's1',
                      skillName: 'Spring Boot',
                      status: 'SCORED',
                      value: 2.0,
                      avgConfidence: 0.6,
                      verifiedEvidenceCount: 1,
                      reviewStatus: 'flagged',
                    },
                  ],
                },
              ],
            }),
          )
        }
        if (url.endsWith('/studio/configs')) {
          return Promise.resolve(
            jsonResponse([{ configId: 'cfg-1', title: 'Backend', version: 1, createdAt: '2026-11-26T10:00:00Z' }]),
          )
        }
        return attemptFetch(url)
      }),
    )
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/ranking', component: RankingView }],
    })
    await router.push('/ranking')
    await router.isReady()
    const { pinia, queryClient } = createPlugins()
    const wrapper = mount(RankingView, {
      global: { ...dialogGlobal, plugins: [pinia, router, PrimeVue, [VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await wrapper.get('[data-testid="inspect-attempt-att-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="matrix-cell-att-1-s1"]').exists()).toBe(true)

    await wrapper.get('[data-testid="evidence-close-btn"]').trigger('click')
    await settle()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="matrix-cell-att-1-s1"]').exists()).toBe(true)
  })

  it('CohortGapsPanel renders backend SQL stats without client-side recomputation', async () => {
    const configId = 'cfg-1'
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          configId,
          releasedCandidateCount: 5,
          skills: [
            {
              skillId: 's1',
              skillName: 'SQL',
              totalCandidates: 5,
              scoredCount: 4,
              naCount: 1,
              naShare: 0.2,
              meanScore: 2.25,
              levelDistribution: [0, 1, 1, 2, 0],
            },
            {
              skillId: 's2',
              skillName: 'Kubernetes',
              totalCandidates: 5,
              scoredCount: 0,
              naCount: 5,
              naShare: 1.0,
              meanScore: null,
              levelDistribution: [0, 0, 0, 0, 0],
            },
          ],
        }),
      ),
    )

    const { pinia, queryClient } = createPlugins()
    const wrapper = mount(CohortGapsPanel, {
      props: { configId },
      global: { plugins: [pinia, PrimeVue, [VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()

    const chart = wrapper.get('[data-testid="chart"]')
    const option = JSON.parse(chart.attributes('data-option') ?? '{}')
    // AGENTS.md §2 #2: all-N/A skill (meanScore === null) MUST stay null, NEVER plotted as 0!
    expect(option.series[0].data).toEqual([2.25, null])
    expect(wrapper.text()).toContain('SQL')
    expect(wrapper.text()).toContain('2.25')
    expect(wrapper.text()).toContain('20%')
    expect(wrapper.text()).toContain('Kubernetes')
    expect(wrapper.text()).toContain('N/A')
  })
})
