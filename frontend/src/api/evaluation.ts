import { apiFetch } from './client'

export type ReleaseStatus = 'pending' | 'flagged' | 'auto_released' | 'approved' | 'overridden'
export type ScoreStatus = 'SCORED' | 'N/A'
// Only COMPLETED rows carry scores; the others are attempts that still wait for the evaluator.
export type EvaluationStatus = 'QUEUED' | 'RUNNING' | 'FAILED' | 'COMPLETED'

export interface SkillColumn {
  skillId: string
  skillName: string
}

export interface MatrixCell {
  skillId: string
  skillName: string
  status: ScoreStatus
  value: number | null
  avgConfidence: number | null
  verifiedEvidenceCount: number
  reviewStatus: ReleaseStatus
}

export interface MatrixCandidateRow {
  inviteId: string
  attemptId: string
  attemptNo: number
  evaluationStatus: EvaluationStatus
  fitScore: number | null
  coverage: number
  releaseStatus: ReleaseStatus
  flaggedCount: number
  releasedAt: string | null
  cells: MatrixCell[]
}

export interface MatrixResponse {
  configId: string
  formulaVersionId: string | null
  formulaVersionNumber: number
  formulaPreset: string
  minCoverage: number
  skills: SkillColumn[]
  rows: MatrixCandidateRow[]
}

export interface ResponseEvaluationDetail {
  responseEvaluationId: string
  entryId: string
  coreQuestionId: string
  skillId: string
  skillName: string
  questionOrigin: string
  questionWeight: number
  confidenceThreshold: number | null
  run1Level: number | null
  run2Level: number | null
  run1Quote: string | null
  run2Quote: string | null
  quoteVerified: boolean
  verifiedQuote: string | null
  aiRationale: string | null
  aiLevel: number | null
  calculatedConfidence: number
  flagReasons: string[]
  reviewStatus: ReleaseStatus
  effectiveLevel: number | null
  overriddenBy: string | null
  overrideReason: string | null
  reviewedAt: string | null
}

export interface AttemptEvaluationDrilldownResponse {
  attemptId: string
  configId: string
  inviteId: string
  attemptNo: number
  fitScore: number | null
  coverage: number
  minCoverage: number
  releaseStatus: ReleaseStatus
  flaggedCount: number
  releasedAt: string | null
  responses: ResponseEvaluationDetail[]
  skillScores: MatrixCell[]
}

export interface ApproveEvaluationResponse {
  responseEvaluationId: string
  attemptId: string
  effectiveLevel: number | null
  reviewStatus: ReleaseStatus
  reviewedAt: string
  remainingFlaggedCount: number
}

export interface OverrideEvaluationResponse {
  responseEvaluationId: string
  attemptId: string
  aiLevel: number | null
  effectiveLevel: number
  reviewStatus: ReleaseStatus
  overrideReason: string
  reviewedAt: string
  updatedFitScore: number | null
  updatedCoverage: number
  remainingFlaggedCount: number
}

export interface ReleaseAttemptResponse {
  attemptId: string
  releaseStatus: ReleaseStatus
  releasedAt: string
  fitScore: number | null
  coverage: number
}

export interface SkillGapStat {
  skillId: string
  skillName: string
  totalCandidates: number
  scoredCount: number
  naCount: number
  naShare: number | null
  meanScore: number | null
  levelDistribution: number[]
}

export interface CohortGapsResponse {
  configId: string
  releasedCandidateCount: number
  skills: SkillGapStat[]
}

export interface FormulaPresetInfo {
  preset: string
  label: string
  description: string
  maxEntrySkillWeightShare: number
}

export interface FormulaVersionResponse {
  formulaVersionId: string
  configId: string
  versionNumber: number
  preset: string
  maxEntrySkillWeightShare: number
  questionWeights: Record<string, number>
  createdAt: string
  recomputedCandidates: number
}

export interface FormulaPreviewCandidate {
  inviteId: string
  attemptId: string
  fitScore: number | null
  coverage: number
  skillScores: MatrixCell[]
}

export interface FormulaPreviewResponse {
  configId: string
  preset: string
  maxEntrySkillWeightShare: number
  candidates: FormulaPreviewCandidate[]
}

export interface CandidateAdvice {
  strengths: string[]
  improvements: string[]
  notAssessed: string[]
}

export interface CandidateEvidenceItem {
  entryId: string
  coreQuestionId: string
  skillId: string
  skillName: string
  verifiedQuote: string | null
  level: number | null
  confidence: number
  questionWeight: number
}

export interface CandidateConversationTurn {
  entryId: string
  seq: number
  origin: string
  questionText: string
  answerText: string | null
  askedAt: string
  submittedAt: string | null
  timedOut: boolean
}

export interface CandidateTraitScore {
  trait: string
  score: number
  description: string
}

export interface CandidateResultsResponse {
  configId: string
  attemptId?: string | null
  releaseStatus: ReleaseStatus
  released: boolean
  releasedAt?: string | null
  formulaPreset?: string | null
  formulaVersionNumber?: number | null
  fitScore?: number | null
  coverage?: number | null
  skillScores?: MatrixCell[] | null
  evidence?: CandidateEvidenceItem[] | null
  questionWeights?: Record<string, number> | null
  conversationLog?: CandidateConversationTurn[] | null
  advice?: CandidateAdvice | null
  softSkillTraits?: CandidateTraitScore[] | null
}

export interface TranscriptQuestionBlock {
  text: string
  origin: string
  coreQuestionId: string | null
  weight: number
  targetSkills: string[]
  askedAt: string
  expiresAt: string | null
}

export interface TranscriptAnswerBlock {
  text: string | null
  submittedAt: string | null
  durationSec: number
  timedOut: boolean
  pasteSuspected: boolean
  injectionSuspected: boolean
}

export interface TranscriptEntryMetadata {
  promptedTerms: string[]
  unpromptedTerms: string[]
}

export interface TranscriptExportEntry {
  entryId: string
  turn: number
  question: TranscriptQuestionBlock
  answer: TranscriptAnswerBlock
  metadata: TranscriptEntryMetadata
}

export interface TranscriptExportEvidence {
  evidenceId: string
  entryId: string
  skillId: string
  skillName: string
  quote: string | null
  quoteVerified: boolean
  suggestedLevel: number | null
  judgeRuns: number[]
  rationale: string | null
}

export interface TranscriptExportResponse {
  format: string
  transcriptId: string
  candidateId: string
  configId: string
  configVersion: string
  attempt: number
  startedAt: string
  entries: TranscriptExportEntry[]
  evidence: TranscriptExportEvidence[]
}

const RANKING_BASE = '/api/v1/ranking'

export function getSkillsMatrix(configId: string): Promise<MatrixResponse> {
  return apiFetch<MatrixResponse>(`${RANKING_BASE}/configs/${encodeURIComponent(configId)}/matrix`)
}

export function getAttemptEvaluations(attemptId: string): Promise<AttemptEvaluationDrilldownResponse> {
  return apiFetch<AttemptEvaluationDrilldownResponse>(
    `${RANKING_BASE}/attempts/${encodeURIComponent(attemptId)}/evaluations`,
  )
}

export function approveEvaluation(responseEvalId: string): Promise<ApproveEvaluationResponse> {
  return apiFetch<ApproveEvaluationResponse>(
    `${RANKING_BASE}/evaluations/${encodeURIComponent(responseEvalId)}/approve`,
    { method: 'POST' },
  )
}

export function overrideEvaluation(
  responseEvalId: string,
  newLevel: number,
  reason: string,
): Promise<OverrideEvaluationResponse> {
  return apiFetch<OverrideEvaluationResponse>(
    `${RANKING_BASE}/evaluations/${encodeURIComponent(responseEvalId)}/override`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ newLevel, reason }),
    },
  )
}

export function releaseAttempt(attemptId: string): Promise<ReleaseAttemptResponse> {
  return apiFetch<ReleaseAttemptResponse>(
    `${RANKING_BASE}/attempts/${encodeURIComponent(attemptId)}/release`,
    { method: 'POST' },
  )
}

export function getCohortGaps(configId: string): Promise<CohortGapsResponse> {
  return apiFetch<CohortGapsResponse>(`${RANKING_BASE}/configs/${encodeURIComponent(configId)}/cohort-gaps`)
}

export function getTranscriptExport(attemptId: string): Promise<TranscriptExportResponse> {
  return apiFetch<TranscriptExportResponse>(`${RANKING_BASE}/transcripts/${encodeURIComponent(attemptId)}`)
}

export function listFormulaPresets(): Promise<FormulaPresetInfo[]> {
  return apiFetch<FormulaPresetInfo[]>(`${RANKING_BASE}/formula-presets`)
}

export function applyFormulaPreset(configId: string, preset: string): Promise<FormulaVersionResponse> {
  return apiFetch<FormulaVersionResponse>(`${RANKING_BASE}/configs/${encodeURIComponent(configId)}/formula`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ preset }),
  })
}

export function previewFormulaPreset(configId: string, preset: string): Promise<FormulaPreviewResponse> {
  return apiFetch<FormulaPreviewResponse>(
    `${RANKING_BASE}/configs/${encodeURIComponent(configId)}/formula/preview`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ preset }),
    },
  )
}

export function getCandidateResults(configId: string): Promise<CandidateResultsResponse> {
  return apiFetch<CandidateResultsResponse>(`/api/v1/assessment/results/${encodeURIComponent(configId)}`)
}

export const formatScore = (val: number | null | undefined): string =>
  val === null || val === undefined ? 'N/A' : val.toFixed(2)

export const formatRatioPercent = (ratio: number | null | undefined): string =>
  ratio === null || ratio === undefined ? '—' : `${Math.round(ratio * 100)}%`
