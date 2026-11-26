import { flushPromises, mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TranscriptDialog from './TranscriptDialog.vue'
import type { TranscriptTurn } from './transcript'

const markers = { timedOut: false, pasteSuspected: false, injectionSuspected: false }

const turns: TranscriptTurn[] = [
  {
    id: 'e-1',
    turn: 1,
    origin: 'core',
    question: 'How do you keep code safe?',
    askedAt: '2026-11-26T10:00:00Z',
    answer: 'I write tests.\nThen I refactor.',
    submittedAt: '2026-11-26T10:01:12Z',
    durationSec: 72,
    markers: { ...markers, pasteSuspected: true },
  },
  {
    id: 'e-2',
    turn: 2,
    origin: 'follow_up',
    question: 'Which tests?',
    askedAt: '2026-11-26T10:02:00Z',
    answer: null,
    submittedAt: null,
    durationSec: null,
    markers: { ...markers, timedOut: true },
  },
]

async function mountDialog(props: Record<string, unknown> = {}) {
  const wrapper = mount(TranscriptDialog, {
    props: { visible: true, title: 'Conversation', turns, showMarkers: false, ...props },
    global: { plugins: [PrimeVue], stubs: { teleport: true } },
  })
  await flushPromises() // the Dialog portal mounts one tick late
  return wrapper
}

describe('TranscriptDialog (REQ-SM-10, REQ-CR-07)', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders interviewer and candidate bubbles in turn order with origin labels', async () => {
    const wrapper = await mountDialog()
    const text = wrapper.get('[data-testid="transcript-turns"]').text()
    expect(text.indexOf('Core question 1')).toBeGreaterThan(-1)
    expect(text.indexOf('Core question 1')).toBeLessThan(text.indexOf('Follow-up'))
    expect(text.indexOf('How do you keep code safe?')).toBeLessThan(text.indexOf('I write tests.'))
    expect(text.indexOf('I write tests.')).toBeLessThan(text.indexOf('Which tests?'))
    expect(wrapper.get('[data-testid="answer-e-1"]').classes()).toContain('whitespace-pre-wrap')
    expect(wrapper.get('[data-testid="answer-meta-e-1"]').text()).toMatch(/answered in 1 min 12 s · \d{2}:\d{2}/)
    expect(wrapper.find('[aria-label="Turn 2, Follow-up"]').exists()).toBe(true)
  })

  it('shows a muted "No answer" bubble for a null answer', async () => {
    const wrapper = await mountDialog()
    expect(wrapper.get('[data-testid="no-answer-e-2"]').text()).toBe('No answer')
    expect(wrapper.find('[data-testid="answer-e-2"]').exists()).toBe(false)
  })

  it('shows marker chips and flag counts only with showMarkers', async () => {
    const hidden = await mountDialog()
    expect(hidden.find('[data-testid="marker-paste-e-1"]').exists()).toBe(false)
    expect(hidden.find('[data-testid="marker-timed-out-e-2"]').exists()).toBe(false)
    expect(hidden.find('[data-testid="summary-flagged"]').exists()).toBe(false)

    const shown = await mountDialog({ showMarkers: true })
    expect(shown.get('[data-testid="marker-paste-e-1"]').text()).toBe('Paste suspected')
    expect(shown.get('[data-testid="marker-timed-out-e-2"]').text()).toBe('Timed out')
    expect(shown.get('[data-testid="summary-flagged"]').text()).toContain('1')
  })

  it('wraps a verified quote in <mark> and never renders markup from the answer', async () => {
    const risky: TranscriptTurn[] = [{ ...turns[0], answer: 'I use <b>tests</b> daily' }]
    const wrapper = await mountDialog({ turns: risky, highlight: { 'e-1': ['<b>tests</b>'] } })
    const mark = wrapper.get('[data-testid="answer-e-1"] mark')
    expect(mark.text()).toBe('<b>tests</b>')
    // Segments add no stray whitespace inside the pre-wrap text.
    expect(wrapper.get('[data-testid="answer-e-1"]').text()).toBe('I use <b>tests</b> daily')
    expect(wrapper.find('[data-testid="answer-e-1"] b').exists()).toBe(false)
  })

  it('scrolls the focused turn into view and outlines it', async () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    const wrapper = await mountDialog({ focusEntryId: 'e-2' })
    await wrapper.setProps({ focusEntryId: 'e-1' })
    await wrapper.vm.$nextTick()
    expect(scrollIntoView).toHaveBeenCalled()
    expect(wrapper.get('[data-testid="turn-e-1"]').classes()).toContain('ring-2')
    expect(wrapper.get('[data-testid="turn-e-2"]').classes()).not.toContain('ring-2')
  })

  it('shows loading, error and empty states', async () => {
    expect((await mountDialog({ loading: true, turns: [] })).text()).toContain('Loading conversation')
    expect((await mountDialog({ error: true, turns: [] })).text()).toContain('Could not load the conversation')
    expect((await mountDialog({ turns: [] })).text()).toContain('No conversation entries recorded')
  })
})
