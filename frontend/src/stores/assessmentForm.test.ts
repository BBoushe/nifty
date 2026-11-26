import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ASSESSMENT_FORM_STORAGE_KEY, useAssessmentFormStore } from './assessmentForm'

describe('useAssessmentFormStore (US-ST-06, Decisions 2.21, 2.22, 2.23)', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('persists draft changes to localStorage and restores them in a new store instance', () => {
    const store = useAssessmentFormStore()
    store.settings.title = 'SE Block Week 2026'
    store.settings.minCoveragePercent = 75
    store.addSkill('Java')
    store.skills[0].rubricLevels = ['None', 'Basic', 'Developing', 'Proficient', 'Expert']
    store.addQuestion()
    store.questions[0].text = 'Explain ACID'
    store.questions[0].targetSkillNames = ['Java']
    store.persist()

    expect(localStorage.getItem(ASSESSMENT_FORM_STORAGE_KEY)).toContain('SE Block Week 2026')

    setActivePinia(createPinia())
    const restored = useAssessmentFormStore()
    expect(restored.settings.title).toBe('SE Block Week 2026')
    expect(restored.settings.minCoveragePercent).toBe(75)
    expect(restored.skills[0].name).toBe('Java')
    expect(restored.questions[0].targetSkillNames).toEqual(['Java'])
  })

  it('removes a deleted skill from every question targetSkillNames list', () => {
    const store = useAssessmentFormStore()
    store.addSkill('Java')
    store.addSkill('Git')
    store.addQuestion()
    store.questions[0].targetSkillNames = ['Java', 'Git']

    store.removeSkill(0)

    expect(store.skills.map((s) => s.name)).toEqual(['Git'])
    expect(store.questions[0].targetSkillNames).toEqual(['Git'])
  })

  it('reorders questions cleanly and keeps gap-free 1-based positions in payload', () => {
    const store = useAssessmentFormStore()
    store.addSkill('Java')
    store.addQuestion()
    store.questions[0].text = 'First'
    store.addQuestion()
    store.questions[1].text = 'Second'

    store.moveQuestion(1, -1)

    expect(store.questions.map((q) => q.text)).toEqual(['Second', 'First'])
    const payload = store.toApiPayload()
    expect(payload.questions.map((q) => q.position)).toEqual([1, 2])
    expect(payload.questions.map((q) => q.text)).toEqual(['Second', 'First'])
  })

  it('reuses the same Idempotency-Key across retries and clears it on reset', () => {
    const store = useAssessmentFormStore()
    const key1 = store.ensureIdempotencyKey()
    const key2 = store.ensureIdempotencyKey()

    expect(key1).toBeTruthy()
    expect(key2).toBe(key1)

    store.reset()
    const keyAfterReset = store.ensureIdempotencyKey()
    expect(keyAfterReset).not.toBe(key1)
  })

  it('converts UI percentage inputs into 0..1 decimals in the API payload', () => {
    const store = useAssessmentFormStore()
    store.settings.minCoveragePercent = 65
    store.settings.combinedHardWeightPercent = 60
    store.settings.alphaPercent = 40
    store.addSkill('Java')
    store.addQuestion()
    store.questions[0].confidenceThresholdPercent = 75

    const payload = store.toApiPayload()
    expect(payload.settings.minCoverage).toBe(0.65)
    expect(payload.settings.combinedHardWeight).toBe(0.6)
    expect(payload.settings.alpha).toBe(0.4)
    expect(payload.questions[0].confidenceThreshold).toBe(0.75)
  })

  it('renames a skill across bound questions and drops blank entries when cleared', () => {
    const store = useAssessmentFormStore()
    store.addSkill('Java')
    store.addQuestion()
    store.questions[0].targetSkillNames = ['Java']

    store.renameSkill(0, ' Java 21 ')
    expect(store.questions[0].targetSkillNames).toEqual(['Java 21'])

    store.renameSkill(0, '   ')
    expect(store.questions[0].targetSkillNames).toEqual([])
  })

  it('initializes all 5 Big Five trait weights summing to 1.0 when switching to CUSTOM preset', () => {
    const store = useAssessmentFormStore()
    store.setSoftPreset('CUSTOM')

    expect(Object.keys(store.settings.constraintWeights)).toEqual([
      'conscientiousness',
      'agreeableness',
      'neuroticism',
      'extraversion',
      'openness',
    ])
    const sum = Object.values(store.settings.constraintWeights).reduce((acc, v) => acc + v, 0)
    expect(sum).toBeCloseTo(1.0, 5)
  })

  it('keeps the form working when localStorage refuses to write (quota or privacy mode)', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('The quota has been exceeded.', 'QuotaExceededError')
    })
    const removeItem = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('Access denied.', 'SecurityError')
    })
    try {
      const store = useAssessmentFormStore()

      expect(() => store.addSkill('Java')).not.toThrow()
      expect(() => (store.settings.title = 'Still editable')).not.toThrow()
      expect(() => store.ensureIdempotencyKey()).not.toThrow()
      expect(store.skills.map((s) => s.name)).toEqual(['Java'])
      expect(store.settings.title).toBe('Still editable')
      expect(store.idempotencyKey).toBeTruthy()
      expect(setItem).toHaveBeenCalled()

      expect(() => store.reset()).not.toThrow()
      expect(removeItem).toHaveBeenCalled()
      expect(store.skills).toEqual([])
    } finally {
      setItem.mockRestore()
      removeItem.mockRestore()
    }
  })
})
