import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CohortGapsPanel from './CohortGapsPanel.vue'
import EvidenceDrawer from './EvidenceDrawer.vue'
import SkillsMatrixPanel from './SkillsMatrixPanel.vue'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

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

  it('SkillsMatrixPanel renders hatched N/A, flagged red border, separate fit/coverage, and bulk-approve skipping flagged', async () => {
    const configId = '11111111-1111-1111-1111-111111111111'
    const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
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
            ],
          }),
        )
      }
      if (url.includes('/evaluations/approve') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse({ approvedCount: 2, skippedFlaggedCount: 1 }))
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

    // Bulk approve skips flagged items and shows feedback
    await wrapper.get('[data-testid="bulk-approve-btn"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('2 approved')
    expect(wrapper.text()).toContain('1 flagged skipped')
  })

  it('EvidenceDrawer displays verified quote, requires override reason, submits override, and blocks release while flagged', async () => {
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
    const wrapper = mount(EvidenceDrawer, {
      props: { attemptId, visible: true },
      global: { plugins: [pinia, PrimeVue, [VueQueryPlugin, { queryClient }]] },
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
