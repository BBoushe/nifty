import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ScoreBadge from './ScoreBadge.vue'

const LABELS = ['No evidence of skill', 'Basic', 'Developing', 'Proficient', 'Expert']

describe('ScoreBadge (REQ-FF-01)', () => {
  it.each([0, 1, 2, 3, 4])('shows level %i as "<level>/4 · <label>"', (level) => {
    const wrapper = mount(ScoreBadge, { props: { level, label: LABELS[level] } })
    expect(wrapper.text()).toBe(`${level}/4 · ${LABELS[level]}`)
    expect(wrapper.classes()).not.toContain('score-hatch')
  })

  it('shows the level alone when no label is given', () => {
    expect(mount(ScoreBadge, { props: { level: 3 } }).text()).toBe('3/4')
  })

  it.each([undefined, null])('shows %s as hatched N/A and never as 0', (level) => {
    const wrapper = mount(ScoreBadge, { props: { level } })
    expect(wrapper.text()).toBe('N/A · not enough evidence')
    expect(wrapper.text()).not.toContain('0')
    expect(wrapper.classes()).toContain('score-hatch')
  })

  it('uses a different fill for each score band', () => {
    const fills = [0, 2, 3, 4].map((level) => mount(ScoreBadge, { props: { level } }).classes().join(' '))
    expect(new Set(fills).size).toBe(4)
  })

  it('treats levels 0 and 1 as the same danger band', () => {
    const [zero, one] = [0, 1].map((level) => mount(ScoreBadge, { props: { level } }).classes().join(' '))
    expect(zero).toBe(one)
  })
})
