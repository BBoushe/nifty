import { apiFetch } from './client'

export type GroupingMode = 'HARD_SKILLS' | 'SOFT_SKILLS' | 'COMBINED'
export type SoftPreset = 'ENGINEERING_DEFAULT' | 'EQUAL_WEIGHTS' | 'CUSTOM'

export interface SettingsInputPayload {
  title: string
  vision: string
  minCoverage: number
  maxAttempts: number
  inventedQuestions: boolean
  bfi10Enabled: boolean
  teamSize: number
  groupingMode: GroupingMode
  softPreset: SoftPreset
  combinedHardWeight: number
  alpha: number
  constraintWeights: Record<string, number>
}

export interface SkillInputPayload {
  name: string
  rubricLevels: string[]
}

export interface QuestionInputPayload {
  position: number
  text: string
  weight: number
  confidenceThreshold: number
  minTimeSec: number
  maxTimeSec: number
  followUpLimit: number
  targetSkillNames: string[]
}

export interface CreateAssessmentPayload {
  settings: SettingsInputPayload
  skills: SkillInputPayload[]
  questions: QuestionInputPayload[]
}

export interface AssessmentSummary {
  configId: string
  title: string
  groupingMode: GroupingMode
  bfi10Enabled: boolean
  teamSize: number
  skillCount: number
  questionCount: number
  status: 'PUBLISHED'
  version: number
  createdAt: string
}

export interface SkillDetail {
  skillId: string
  name: string
  rubricLevels: string[]
}

export interface QuestionDetail {
  coreQuestionId: string
  position: number
  text: string
  weight: number
  confidenceThreshold: number
  minTimeSec: number
  maxTimeSec: number
  followUpLimit: number
  targetSkillIds: string[]
  targetSkillNames: string[]
}

export interface AssessmentDetail {
  configId: string
  title: string
  vision: string
  minCoverage: number
  maxAttempts: number
  inventedQuestions: boolean
  bfi10Enabled: boolean
  teamSize: number
  groupingMode: GroupingMode
  softPreset: SoftPreset
  combinedHardWeight: number
  alpha: number
  constraintWeights: Record<string, number>
  status: 'PUBLISHED'
  version: number
  createdAt: string
  skills: SkillDetail[]
  questions: QuestionDetail[]
}

const STUDIO_BASE = '/api/v1/ranking/studio/configs'

export function listAssessments(): Promise<AssessmentSummary[]> {
  return apiFetch<AssessmentSummary[]>(STUDIO_BASE)
}

export function getAssessment(configId: string): Promise<AssessmentDetail> {
  return apiFetch<AssessmentDetail>(`${STUDIO_BASE}/${encodeURIComponent(configId)}`)
}

export function createAssessment(
  idempotencyKey: string,
  payload: CreateAssessmentPayload,
): Promise<AssessmentDetail> {
  return apiFetch<AssessmentDetail>(STUDIO_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(payload),
  })
}

export function deleteAssessment(configId: string): Promise<void> {
  return apiFetch<void>(`${STUDIO_BASE}/${encodeURIComponent(configId)}`, {
    method: 'DELETE',
  })
}

export const formatPercent = (ratio: number): string => `${Math.round(ratio * 100)}%`
