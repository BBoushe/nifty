import { apiFetch } from './client'

// Mirrors TeamProposalResponse (docs/api.md §4). Skill values are 0..4, traits 1..5 (already reverse-scored);
// null means N/A and is never shown as 0 (REQ-TB-11, Decision 4.3).
export interface TeamMember {
  inviteId: string
  candidateRef: string
  skillScores: Record<string, number | null>
  traits: Record<string, number>
}

export interface TeamCard {
  teamNo: number
  size: number
  members: TeamMember[]
  skillAverages: Record<string, number | null>
  skillNaCounts: Record<string, number>
  traitAverages: Record<string, number | null>
}

export interface TeamProposal {
  proposalId: string
  configId: string
  status: 'DRAFT' | 'PUBLISHED'
  numTeams: number
  groupingMode: 'HARD_SKILLS' | 'SOFT_SKILLS' | 'COMBINED'
  alpha: number
  combinedHardWeight: number
  seed: number
  createdAt: string
  publishedAt: string | null
  diagnostics: { imbalance: number; diversity: number; cost: number; naCount: number }
  teams: TeamCard[]
  pendingInviteIds: string[]
}

export interface GenerateTeamsInput {
  numTeams?: number
  groupingMode?: TeamProposal['groupingMode']
}

export interface Assignment {
  inviteId: string
  teamNo: number
}

const configTeams = (configId: string) =>
  `/api/v1/ranking/configs/${encodeURIComponent(configId)}/teams`
const proposalPath = (proposalId: string) => `/api/v1/ranking/teams/${encodeURIComponent(proposalId)}`

const post = <T>(url: string, body?: unknown) =>
  apiFetch<T>(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

export const listTeams = (configId: string) => apiFetch<TeamProposal[]>(configTeams(configId))

export const generateTeams = (configId: string, input: GenerateTeamsInput) =>
  post<TeamProposal>(`${configTeams(configId)}/generate`, input)

export const recalculateTeams = (proposalId: string, assignments: Assignment[]) =>
  post<TeamProposal>(`${proposalPath(proposalId)}/recalculate`, { assignments })

export const publishTeams = (proposalId: string) => post<TeamProposal>(`${proposalPath(proposalId)}/publish`)
