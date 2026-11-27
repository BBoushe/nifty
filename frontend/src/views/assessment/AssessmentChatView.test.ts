import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AssessmentChatView from './AssessmentChatView.vue'

const T0 = new Date('2026-11-26T10:00:00Z')
const ATTEMPT = '11111111-1111-1111-1111-111111111111'
const at = (seconds: number) => new Date(T0.getTime() + seconds * 1000).toISOString()

const sse = (events: [string, object][]) => {
  const text = events.map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join('')
  return new Response(text, { headers: { 'Content-Type': 'text/event-stream' } })
}
const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })

const firstQuestion = () =>
  sse([
    ['delta', { text: 'Hello. ' }],
    ['delta', { text: 'What is ACID?' }],
    [
      'question',
      { attemptId: ATTEMPT, entryId: 'e1', origin: 'core', expiresAt: at(180), questionsRemaining: 2, serverTime: at(0) },
    ],
  ])

const CONFIG = '22222222-2222-2222-2222-222222222222'

function mountView() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const pinia = createPinia()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/assessment/results/:configId', component: { template: '<div />' } }],
  })
  useAuthStore(pinia).me = { role: 'CANDIDATE', professorId: null, configId: CONFIG }
  return mount(AssessmentChatView, { global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient }], PrimeVue] } })
}

describe('AssessmentChatView (REQ-AC-03, 05, 06, 10, 20)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    // Only the clock and intervals are fake, so flushPromises keeps working.
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(T0)
    sessionStorage.clear()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('streams the first question and shows the countdown and questions left, with no pause or scores', async () => {
    fetchMock.mockResolvedValueOnce(firstQuestion())
    const wrapper = mountView()

    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Hello. What is ACID?')
    expect(wrapper.get('[data-testid="countdown"]').text()).toBe('3:00')
    expect(wrapper.get('[data-testid="questions-left"]').text()).toContain('2')
    expect(wrapper.findAll('button').some((b) => /pause|resume/i.test(b.text()))).toBe(false)
    expect(wrapper.text()).not.toMatch(/score|weight|formula|trait|confidence/i)
    expect(sessionStorage.getItem('nifty.assessment.attemptId')).toBe(ATTEMPT)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/assessment/sessions')
  })

  it('submits the typed text automatically when the countdown reaches zero', async () => {
    fetchMock.mockResolvedValueOnce(firstQuestion())
    fetchMock.mockResolvedValueOnce(sse([['done', { status: 'COMPLETED' }]]))
    const wrapper = mountView()
    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="answer"]').setValue('Partial answer')
    vi.advanceTimersByTime(180_000)
    await flushPromises()

    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe(`/api/v1/assessment/sessions/${ATTEMPT}/answers`)
    expect(JSON.parse(init.body)).toEqual({ content: 'Partial answer' })
    expect(wrapper.find('[data-testid="completed"]').exists()).toBe(true)
    // REQ-NAV-07
    expect(wrapper.get('[data-testid="results-link"]').attributes('href')).toBe(`/assessment/results/${CONFIG}`)
  })

  it('resumes after a refresh with the same deadline', async () => {
    sessionStorage.setItem('nifty.assessment.attemptId', ATTEMPT)
    fetchMock.mockResolvedValueOnce(
      json({
        attemptId: ATTEMPT,
        status: 'IN_PROGRESS',
        attemptNo: 1,
        bfi10Enabled: false,
        questionsRemaining: 3,
        expiresAt: at(100),
        messages: [{ seq: 5, author: 'interviewer', content: 'Question 3', createdAt: at(-80) }],
      }),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(fetchMock.mock.calls[0][0]).toBe(`/api/v1/assessment/sessions/${ATTEMPT}`)
    expect(wrapper.text()).toContain('Question 3')
    expect(wrapper.get('[data-testid="countdown"]').text()).toBe('1:40')
    expect(wrapper.find('[data-testid="start"]').exists()).toBe(false)
  })

  it('counts down on the server clock when the browser clock is behind', async () => {
    fetchMock.mockResolvedValueOnce(
      sse([
        ['delta', { text: 'Q1?' }],
        [
          'question',
          // The server is 20 s ahead of this browser, so 160 s are really left.
          { attemptId: ATTEMPT, entryId: 'e1', origin: 'core', expiresAt: at(180), questionsRemaining: 0, serverTime: at(20) },
        ],
      ]),
    )
    const wrapper = mountView()
    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="countdown"]').text()).toBe('2:40')
  })

  it('polls a resumed session until the interviewer question arrives', async () => {
    sessionStorage.setItem('nifty.assessment.attemptId', ATTEMPT)
    const waiting = {
      attemptId: ATTEMPT,
      status: 'IN_PROGRESS',
      attemptNo: 1,
      bfi10Enabled: false,
      questionsRemaining: 1,
      expiresAt: null,
      serverTime: at(0),
      messages: [
        { seq: 1, author: 'interviewer', content: 'Question 1', createdAt: at(-60) },
        { seq: 2, author: 'candidate', content: 'My answer', createdAt: at(-1) },
      ],
    }
    fetchMock.mockResolvedValueOnce(json(waiting))
    fetchMock.mockResolvedValueOnce(
      json({
        ...waiting,
        expiresAt: at(120),
        messages: [...waiting.messages, { seq: 3, author: 'interviewer', content: 'Question 2', createdAt: at(0) }],
      }),
    )
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="countdown"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('waiting for the interviewer')

    vi.advanceTimersByTime(3000)
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Question 2')
    expect(wrapper.get('[data-testid="countdown"]').text()).toBe('2:00')
  })

  it('caps the answer at 4000 characters and counts them', async () => {
    fetchMock.mockResolvedValueOnce(firstQuestion())
    const wrapper = mountView()
    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()

    const answer = wrapper.get('[data-testid="answer"]')
    expect(answer.attributes('maxlength')).toBe('4000')
    await answer.setValue('abcdefg')
    expect(wrapper.get('[data-testid="answer-counter"]').text()).toBe('7 / 4000')
  })

  it('opens the BFI-10 form straight away for a SOFT_SKILLS-only start', async () => {
    fetchMock.mockResolvedValueOnce(
      json({ attemptId: ATTEMPT, status: 'BFI10', attemptNo: 1, bfi10Enabled: true, questionsRemaining: 0,
        expiresAt: null, messages: [] }),
    )
    const wrapper = mountView()
    await wrapper.get('[data-testid="start"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="bfi10-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="countdown"]').exists()).toBe(false)
  })

  describe('retake (REQ-AC-22)', () => {
    const NEXT_ATTEMPT = '33333333-3333-3333-3333-333333333333'
    const completedState = (attemptsLeft: number) =>
      json({
        attemptId: ATTEMPT,
        status: 'COMPLETED',
        attemptNo: 1,
        bfi10Enabled: false,
        questionsRemaining: 0,
        attemptsLeft,
        expiresAt: null,
        messages: [],
      })

    it('a stored COMPLETED attempt with an attempt left shows Start new attempt, and a click clears the stored ID and starts again', async () => {
      sessionStorage.setItem('nifty.assessment.attemptId', ATTEMPT)
      let storedAtStart: string | null = 'unset'
      fetchMock.mockResolvedValueOnce(completedState(1))
      fetchMock.mockImplementationOnce(() => {
        storedAtStart = sessionStorage.getItem('nifty.assessment.attemptId')
        return Promise.resolve(
          sse([
            ['delta', { text: 'Second attempt?' }],
            [
              'question',
              { attemptId: NEXT_ATTEMPT, entryId: 'e9', origin: 'core', expiresAt: at(180), questionsRemaining: 2, serverTime: at(0) },
            ],
          ]),
        )
      })
      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.get('[data-testid="start-new"]').text()).toBe('Start new attempt')
      expect(wrapper.find('[data-testid="no-attempts-left"]').exists()).toBe(false)

      await wrapper.get('[data-testid="start-new"]').trigger('click')
      await flushPromises()

      expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/assessment/sessions')
      expect(storedAtStart).toBeNull()
      expect(sessionStorage.getItem('nifty.assessment.attemptId')).toBe(NEXT_ATTEMPT)
      expect(wrapper.text()).toContain('Second attempt?')
      expect(wrapper.find('[data-testid="completed"]').exists()).toBe(false)
    })

    it('a stored COMPLETED attempt with no attempt left shows No attempts left and no start button', async () => {
      sessionStorage.setItem('nifty.assessment.attemptId', ATTEMPT)
      fetchMock.mockResolvedValueOnce(completedState(0))
      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.get('[data-testid="no-attempts-left"]').text()).toContain('No attempts left')
      expect(wrapper.find('[data-testid="start-new"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="start"]').exists()).toBe(false)
    })

    it('a new tab learns from the 409 on start that no attempt is left, and hides the start button', async () => {
      fetchMock.mockResolvedValueOnce(
        new Response(
          JSON.stringify({ title: 'Conflict', status: 409, detail: 'You have used all attempts for this assessment.' }),
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      const wrapper = mountView()
      await wrapper.get('[data-testid="start"]').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="no-attempts-left"]').text()).toContain('No attempts left')
      expect(wrapper.find('[data-testid="start"]').exists()).toBe(false)
    })

    it('an attempt that finishes in this page fetches attemptsLeft and offers the retake', async () => {
      fetchMock.mockResolvedValueOnce(firstQuestion())
      fetchMock.mockResolvedValueOnce(sse([['done', { status: 'COMPLETED' }]]))
      fetchMock.mockResolvedValueOnce(completedState(1))
      const wrapper = mountView()
      await wrapper.get('[data-testid="start"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="answer"]').setValue('My answer')
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(fetchMock.mock.calls[2][0]).toBe(`/api/v1/assessment/sessions/${ATTEMPT}`)
      expect(wrapper.get('[data-testid="start-new"]').text()).toBe('Start new attempt')
    })
  })
})
