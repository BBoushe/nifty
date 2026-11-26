import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import ResultsView from './ResultsView.vue'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

async function mountResults(configId: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/assessment/results/:configId', component: ResultsView }],
  })
  await router.push(`/assessment/results/${configId}`)
  await router.isReady()

  const wrapper = mount(ResultsView, {
    // Teleport is stubbed so the test can query the Dialog content.
    global: { stubs: { teleport: true }, plugins: [pinia, router, PrimeVue, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

describe('Candidate ResultsView (US-EV-06, REQ-CR-01..06)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows pending review gate and withholds scores/weights/traits before release', async () => {
    const configId = '11111111-1111-1111-1111-111111111111'
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          configId,
          releaseStatus: 'pending',
          released: false,
        }),
      ),
    )

    const wrapper = await mountResults(configId)
    expect(wrapper.find('[data-testid="results-pending"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="results-released"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('WEIGHTED_MEAN')
  })

  it('shows released scores with hatched N/A, verified quotes, conversation log, advice, and neutral BFI-10 traits', async () => {
    const configId = '11111111-1111-1111-1111-111111111111'
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          configId,
          attemptId: 'att-42',
          releaseStatus: 'auto_released',
          released: true,
          releasedAt: '2026-11-26T12:00:00Z',
          formulaPreset: 'WEIGHTED_MEAN',
          formulaVersionNumber: 1,
          fitScore: 2.5,
          coverage: 0.75,
          skillScores: [
            {
              skillId: 's1',
              skillName: 'testing',
              status: 'SCORED',
              value: 4.0,
              avgConfidence: 0.95,
              verifiedEvidenceCount: 2,
              reviewStatus: 'auto_released',
            },
            {
              skillId: 's2',
              skillName: 'sql',
              status: 'SCORED',
              value: 1.0,
              avgConfidence: 0.88,
              verifiedEvidenceCount: 1,
              reviewStatus: 'auto_released',
            },
            {
              skillId: 's3',
              skillName: 'docker',
              status: 'N/A',
              value: null,
              avgConfidence: null,
              verifiedEvidenceCount: 0,
              reviewStatus: 'auto_released',
            },
          ],
          evidence: [
            {
              entryId: 'e1',
              coreQuestionId: 'q1',
              skillId: 's1',
              skillName: 'testing',
              verifiedQuote: 'write unit tests before refactoring',
              level: 4,
              confidence: 0.95,
              questionWeight: 2.0,
            },
          ],
          questionWeights: { q1: 2.0 },
          conversationLog: [
            {
              entryId: 'e1',
              seq: 1,
              origin: 'core',
              questionText: 'How do you ensure code safety?',
              answerText: 'I write unit tests before refactoring.',
              askedAt: '2026-11-26T11:50:00Z',
              submittedAt: '2026-11-26T11:51:00Z',
              timedOut: false,
            },
          ],
          advice: {
            strengths: ['testing'],
            improvements: ['sql'],
            notAssessed: ['docker'],
          },
          softSkillTraits: [
            {
              trait: 'conscientiousness',
              score: 4.5,
              description: 'Reflects how you organize tasks, plan ahead, and follow structured workflows.',
            },
          ],
        }),
      ),
    )

    const wrapper = await mountResults(configId)
    expect(wrapper.find('[data-testid="results-released"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('2.50')
    expect(wrapper.text()).toContain('75%')
    expect(wrapper.text()).toContain('WEIGHTED_MEAN')
    expect(wrapper.text()).toContain('write unit tests before refactoring')
    // REQ-CR-07: the conversation is behind a button, not inline.
    expect(wrapper.text()).not.toContain('How do you ensure code safety?')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await wrapper.get('[data-testid="open-conversation-btn"]').trigger('click')
    await flushPromises()
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('How do you ensure code safety?')
    expect(dialog.text()).toContain('I write unit tests before refactoring.')
    expect(dialog.text()).toContain('Core question 1')
    // The candidate never sees professor markers.
    expect(dialog.find('[data-testid="marker-timed-out-e1"]').exists()).toBe(false)
    expect(dialog.find('[data-testid="summary-flagged"]').exists()).toBe(false)

    // N/A skill rendered with hatch, and listed under notAssessed rather than weak
    const naBadge = wrapper.get('[data-testid="result-skill-s3"]')
    expect(naBadge.html()).toContain('score-hatch')
    expect(wrapper.get('[data-testid="advice-strengths"]').text()).toContain('testing')
    expect(wrapper.get('[data-testid="advice-improvements"]').text()).toContain('sql')
    expect(wrapper.get('[data-testid="advice-not-assessed"]').text()).toContain('docker')

    // Neutral BFI-10 trait display
    expect(wrapper.get('[data-testid="bfi10-traits"]').text()).toContain('conscientiousness')
    expect(wrapper.get('[data-testid="bfi10-traits"]').text()).toContain('4.5 / 5')
  })
})
