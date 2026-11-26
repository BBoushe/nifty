import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAssessmentFormStore } from '@/stores/assessmentForm'
import AssessmentDetailView from './AssessmentDetailView.vue'
import AssessmentListView from './AssessmentListView.vue'
import QuestionsSection from './QuestionsSection.vue'
import ReviewStep from './ReviewStep.vue'
import SettingsSection from './SettingsSection.vue'
import SkillsSection from './SkillsSection.vue'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const problemResponse = (status: number, body: object) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

function createTestPlugins() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/ranking/studio', component: AssessmentListView },
      { path: '/ranking/studio/new', component: { template: '<div>new</div>' } },
      { path: '/ranking/studio/:configId', component: AssessmentDetailView },
      { path: '/ranking/studio/:configId/invites', component: { template: '<div>invites</div>' } },
    ],
  })
  return { pinia, queryClient, router }
}

describe('Studio Views (US-ST-06, Decisions 2.21, 2.22, 2.23)', () => {
  beforeEach(() => {
    localStorage.clear()
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

  it('SettingsSection binds title, groupingMode, and percentage inputs', async () => {
    const { pinia } = createTestPlugins()
    const wrapper = mount(SettingsSection, { global: { plugins: [pinia, PrimeVue] } })
    const store = useAssessmentFormStore()

    await wrapper.get('[data-testid="studio-title"]').setValue('Backend Craft')
    expect(store.settings.title).toBe('Backend Craft')
    expect(wrapper.text()).toContain('Minimum coverage (%)')
  })

  it('SkillsSection renders 5 rubric boxes (0..4) per skill and zero weight inputs', async () => {
    const { pinia } = createTestPlugins()
    const store = useAssessmentFormStore()
    store.addSkill('Java')

    const wrapper = mount(SkillsSection, { global: { plugins: [pinia, PrimeVue] } })

    expect(wrapper.findAll('[data-testid^="rubric-level-0-"]')).toHaveLength(5)
    expect(wrapper.text().toLowerCase()).not.toContain('skill weight')
  })

  it('QuestionsSection renders defined skill options and supports reordering', async () => {
    const { pinia } = createTestPlugins()
    const store = useAssessmentFormStore()
    store.addSkill('Java')
    store.addSkill('Git')
    store.addQuestion()
    store.questions[0].text = 'Q1'
    store.addQuestion()
    store.questions[1].text = 'Q2'

    const wrapper = mount(QuestionsSection, { global: { plugins: [pinia, PrimeVue] } })

    expect(wrapper.text()).toContain('Java')
    expect(wrapper.text()).toContain('Git')

    await wrapper.get('[data-testid="move-question-up-1"]').trigger('click')
    expect(store.questions.map((q) => q.text)).toEqual(['Q2', 'Q1'])
  })

  it('ReviewStep submits with Idempotency-Key and maps 400 ProblemDetail errors', async () => {
    const { pinia, router } = createTestPlugins()
    await router.push('/ranking/studio/new')
    await router.isReady()

    const store = useAssessmentFormStore()
    store.settings.title = 'SE Assessment'
    store.settings.vision = 'Evaluate craft'
    store.addSkill('Java')
    store.skills[0].rubricLevels = ['0', '1', '2', '3', '4']
    store.addQuestion()
    store.questions[0].text = 'Explain SQL'
    store.questions[0].targetSkillNames = ['Java']

    const fetchMock = vi.fn().mockResolvedValueOnce(
      problemResponse(400, {
        status: 400,
        title: 'Bad Request',
        detail: 'Validation failed.',
        errors: [
          { field: 'questions[0].maxTimeSec', message: 'must be greater than minTimeSec', code: 'TIME_RANGE' },
        ],
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(ReviewStep, { global: { plugins: [pinia, router, PrimeVue] } })
    await wrapper.get('[data-testid="submit-assessment"]').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
    expect(headers.get('Idempotency-Key')).toBeTruthy()
    expect(wrapper.text()).toContain('questions[0].maxTimeSec')
    expect(wrapper.text()).toContain('must be greater than minTimeSec')
  })

  it('ReviewStep resets the form and navigates to the new config detail on 201', async () => {
    const { pinia, router } = createTestPlugins()
    await router.push('/ranking/studio/new')
    await router.isReady()

    const store = useAssessmentFormStore()
    store.settings.title = 'Published Assessment'
    store.settings.vision = 'Vision'
    const newId = '55555555-5555-5555-5555-555555555555'

    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(jsonResponse({ configId: newId }, 201)))
    const pushSpy = vi.spyOn(router, 'push')

    const wrapper = mount(ReviewStep, { global: { plugins: [pinia, router, PrimeVue] } })
    await wrapper.get('[data-testid="submit-assessment"]').trigger('click')
    await flushPromises()

    expect(pushSpy).toHaveBeenCalledWith(`/ranking/studio/${newId}`)
    expect(store.settings.title).toBe('')
    expect(localStorage.getItem('nifty.studio.assessmentForm.v1')).toBeNull()
  })

  it('AssessmentListView renders professor assessments and links to create/detail', async () => {
    const { pinia, queryClient, router } = createTestPlugins()
    await router.push('/ranking/studio')
    await router.isReady()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValueOnce(
        jsonResponse([
          {
            configId: '11111111-1111-1111-1111-111111111111',
            title: 'SE Block Week',
            groupingMode: 'COMBINED',
            bfi10Enabled: true,
            teamSize: 4,
            skillCount: 2,
            questionCount: 3,
            status: 'PUBLISHED',
            version: 1,
            createdAt: '2026-11-26T08:00:00Z',
          },
        ]),
      ),
    )

    const wrapper = mount(AssessmentListView, {
      global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }], PrimeVue] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('SE Block Week')
    expect(wrapper.find('a[href="/ranking/studio/11111111-1111-1111-1111-111111111111"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/ranking/studio/new"]').exists()).toBe(true)
  })

  it('AssessmentDetailView renders read-only detail with hidden rubrics and Send to students link', async () => {
    const { pinia, queryClient, router } = createTestPlugins()
    const id = '22222222-2222-2222-2222-222222222222'
    await router.push(`/ranking/studio/${id}`)
    await router.isReady()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValueOnce(
        jsonResponse({
          configId: id,
          title: 'Distributed Systems',
          vision: 'Check consensus understanding',
          minCoverage: 0.7,
          maxAttempts: 2,
          inventedQuestions: false,
          bfi10Enabled: true,
          teamSize: 4,
          groupingMode: 'COMBINED',
          softPreset: 'ENGINEERING_DEFAULT',
          combinedHardWeight: 0.6,
          alpha: 0.5,
          constraintWeights: {},
          status: 'PUBLISHED',
          version: 1,
          createdAt: '2026-11-26T08:00:00Z',
          skills: [
            {
              skillId: '33333333-3333-3333-3333-333333333333',
              name: 'Raft',
              rubricLevels: ['None', 'Basic', 'Developing', 'Proficient', 'Expert'],
            },
          ],
          questions: [
            {
              coreQuestionId: '44444444-4444-4444-4444-444444444444',
              position: 1,
              text: 'How does leader election work?',
              weight: 2,
              confidenceThreshold: 0.75,
              minTimeSec: 30,
              maxTimeSec: 180,
              followUpLimit: 2,
              targetSkillIds: ['33333333-3333-3333-3333-333333333333'],
              targetSkillNames: ['Raft'],
            },
          ],
        }),
      ),
    )

    const wrapper = mount(AssessmentDetailView, {
      global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }], PrimeVue] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Distributed Systems')
    expect(wrapper.text()).toContain('Raft')
    expect(wrapper.text()).toContain('Expert')
    expect(wrapper.text()).toContain('70%')
    const inviteLink = wrapper.find(`a[href="/ranking/studio/${id}/invites"]`)
    expect(inviteLink.exists()).toBe(true)
    expect(inviteLink.text()).toContain('Send to students')
  })
})
