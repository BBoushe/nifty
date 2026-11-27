import { defineStore } from 'pinia'
import { reactive, ref, watch } from 'vue'
import type { CreateAssessmentPayload, GroupingMode, SoftPreset } from '@/api/studio'

export const ASSESSMENT_FORM_STORAGE_KEY = 'nifty.studio.assessmentForm.v1'

export interface DraftSettings {
  title: string
  vision: string
  minCoveragePercent: number
  maxAttempts: number
  bfi10Enabled: boolean
  teamSize: number
  groupingMode: GroupingMode
  softPreset: SoftPreset
  combinedHardWeightPercent: number
  alphaPercent: number
  constraintWeights: Record<string, number>
}

export interface DraftSkill {
  id: string
  name: string
  rubricLevels: [string, string, string, string, string]
}

export interface DraftQuestion {
  id: string
  text: string
  weight: number
  confidenceThresholdPercent: number
  minTimeSec: number
  maxTimeSec: number
  followUpLimit: number
  targetSkillNames: string[]
}

interface PersistedDraft {
  settings: DraftSettings
  skills: DraftSkill[]
  questions: DraftQuestion[]
  idempotencyKey: string | null
}

export const BIG_FIVE_TRAITS = [
  'conscientiousness',
  'agreeableness',
  'neuroticism',
  'extraversion',
  'openness',
] as const

export const defaultCustomTraitWeights = (): Record<string, number> => ({
  conscientiousness: 0.2,
  agreeableness: 0.2,
  neuroticism: 0.2,
  extraversion: 0.2,
  openness: 0.2,
})

const newId = (): string =>
  typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`

const defaultSettings = (): DraftSettings => ({
  title: '',
  vision: '',
  minCoveragePercent: 60,
  maxAttempts: 1,
  bfi10Enabled: false,
  teamSize: 4,
  groupingMode: 'HARD_SKILLS',
  softPreset: 'ENGINEERING_DEFAULT',
  combinedHardWeightPercent: 50,
  alphaPercent: 50,
  constraintWeights: {},
})

const roundRatio = (percent: number): number => Math.round(percent * 100) / 10000

const normalize = (name: string): string => name.trim().toLowerCase()

function loadDraft(): PersistedDraft | null {
  if (typeof localStorage === 'undefined') return null
  try {
    const raw = localStorage.getItem(ASSESSMENT_FORM_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<PersistedDraft>
    return {
      settings: { ...defaultSettings(), ...(parsed.settings ?? {}) },
      skills: Array.isArray(parsed.skills) ? parsed.skills : [],
      questions: Array.isArray(parsed.questions) ? parsed.questions : [],
      idempotencyKey: typeof parsed.idempotencyKey === 'string' ? parsed.idempotencyKey : null,
    }
  } catch {
    return null
  }
}

/**
 * Local browser draft for one-shot immutable assessment creation (Decision 2.21, US-ST-06).
 * Persists to localStorage and reuses one Idempotency-Key across retries until reset().
 */
export const useAssessmentFormStore = defineStore('assessmentForm', () => {
  const initial = loadDraft()
  const settings = reactive<DraftSettings>(initial ? initial.settings : defaultSettings())
  const skills = ref<DraftSkill[]>(initial ? initial.skills : [])
  const questions = ref<DraftQuestion[]>(initial ? initial.questions : [])
  const idempotencyKey = ref<string | null>(initial ? initial.idempotencyKey : null)

  let suppressPersist = false

  function persist(): void {
    if (suppressPersist || typeof localStorage === 'undefined') return
    const snapshot: PersistedDraft = {
      settings: { ...settings, constraintWeights: { ...settings.constraintWeights } },
      skills: skills.value,
      questions: questions.value,
      idempotencyKey: idempotencyKey.value,
    }
    // shortcut: a full or blocked localStorage (quota, privacy mode) silently loses the draft on reload; add a
    // visible "draft not saved" notice when that matters.
    try {
      localStorage.setItem(ASSESSMENT_FORM_STORAGE_KEY, JSON.stringify(snapshot))
    } catch {
      // The form keeps working in memory.
    }
  }

  watch([settings, skills, questions, idempotencyKey], persist, { deep: true, flush: 'sync' })

  function addSkill(name = ''): void {
    skills.value.push({
      id: newId(),
      name,
      rubricLevels: ['', '', '', '', ''],
    })
  }

  function removeSkill(index: number): void {
    const removed = skills.value[index]
    if (!removed) return
    const removedKey = normalize(removed.name)
    skills.value.splice(index, 1)
    if (removedKey) {
      for (const question of questions.value) {
        question.targetSkillNames = question.targetSkillNames.filter((n) => normalize(n) !== removedKey)
      }
    }
  }

  function renameSkill(index: number, nextName: string): void {
    const skill = skills.value[index]
    if (!skill) return
    const previousKey = normalize(skill.name)
    skill.name = nextName
    const trimmedNext = nextName.trim()
    if (previousKey) {
      for (const question of questions.value) {
        if (!trimmedNext) {
          question.targetSkillNames = question.targetSkillNames.filter((n) => normalize(n) !== previousKey)
        } else {
          question.targetSkillNames = question.targetSkillNames.map((n) =>
            normalize(n) === previousKey ? trimmedNext : n,
          )
        }
      }
    }
  }

  function setSoftPreset(preset: SoftPreset): void {
    settings.softPreset = preset
    if (preset === 'CUSTOM') {
      const hasAllFive = BIG_FIVE_TRAITS.every(
        (trait) => typeof settings.constraintWeights[trait] === 'number',
      )
      if (!hasAllFive) {
        settings.constraintWeights = defaultCustomTraitWeights()
      }
    }
  }

  function addQuestion(): void {
    questions.value.push({
      id: newId(),
      text: '',
      weight: 1,
      confidenceThresholdPercent: 70,
      minTimeSec: 30,
      maxTimeSec: 180,
      followUpLimit: 1,
      targetSkillNames: [],
    })
  }

  function removeQuestion(index: number): void {
    questions.value.splice(index, 1)
  }

  function moveQuestion(index: number, delta: -1 | 1): void {
    const target = index + delta
    if (target < 0 || target >= questions.value.length) return
    const [item] = questions.value.splice(index, 1)
    questions.value.splice(target, 0, item)
  }

  function ensureIdempotencyKey(): string {
    if (!idempotencyKey.value) {
      idempotencyKey.value = newId()
      persist()
    }
    return idempotencyKey.value
  }

  function toApiPayload(): CreateAssessmentPayload {
    return {
      settings: {
        title: settings.title,
        vision: settings.vision,
        minCoverage: roundRatio(settings.minCoveragePercent),
        maxAttempts: settings.maxAttempts,
        bfi10Enabled: settings.bfi10Enabled,
        teamSize: settings.teamSize,
        groupingMode: settings.groupingMode,
        softPreset: settings.softPreset,
        combinedHardWeight: roundRatio(settings.combinedHardWeightPercent),
        alpha: roundRatio(settings.alphaPercent),
        constraintWeights: { ...settings.constraintWeights },
      },
      skills: skills.value.map((s) => ({
        name: s.name,
        rubricLevels: [...s.rubricLevels],
      })),
      questions: questions.value.map((q, idx) => ({
        position: idx + 1,
        text: q.text,
        weight: q.weight,
        confidenceThreshold: roundRatio(q.confidenceThresholdPercent),
        minTimeSec: q.minTimeSec,
        maxTimeSec: q.maxTimeSec,
        followUpLimit: q.followUpLimit,
        targetSkillNames: [...q.targetSkillNames],
      })),
    }
  }

  function reset(): void {
    suppressPersist = true
    try {
      Object.assign(settings, defaultSettings())
      skills.value = []
      questions.value = []
      idempotencyKey.value = null
    } finally {
      suppressPersist = false
    }
    try {
      localStorage.removeItem(ASSESSMENT_FORM_STORAGE_KEY)
    } catch {
      // No storage, nothing to remove.
    }
  }

  return {
    settings,
    skills,
    questions,
    idempotencyKey,
    persist,
    addSkill,
    removeSkill,
    renameSkill,
    setSoftPreset,
    addQuestion,
    removeQuestion,
    moveQuestion,
    ensureIdempotencyKey,
    toApiPayload,
    reset,
  }
})
