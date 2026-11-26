import { apiFetch } from './client'

export interface CreatedInvite {
  inviteId: string
  candidateRef: string
  link: string
}

export interface InviteSummary {
  inviteId: string
  candidateRef: string
  revoked: boolean
  createdAt: string
}

const invitesPath = (configId: string) =>
  `/api/v1/ranking/studio/configs/${encodeURIComponent(configId)}/invites`

export function listInvites(configId: string): Promise<InviteSummary[]> {
  return apiFetch<InviteSummary[]>(invitesPath(configId))
}

export function createInvites(configId: string, candidateRefs: string[]): Promise<CreatedInvite[]> {
  return apiFetch<CreatedInvite[]>(invitesPath(configId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ candidateRefs }),
  })
}

export function revokeInvite(configId: string, inviteId: string): Promise<void> {
  return apiFetch<void>(`${invitesPath(configId)}/${encodeURIComponent(inviteId)}`, {
    method: 'DELETE',
  })
}

/** Parses a one-column CSV (`candidate_ref` header optional) into trimmed non-blank candidate refs. */
export function parseCandidateCsv(text: string): string[] {
  const cleaned = text.replace(/^\uFEFF/, '')
  const lines = cleaned.split(/\r?\n/)
  const refs: string[] = []
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i].trim()
    if (!raw) continue
    // Take first CSV column and strip optional double quotes.
    const commaIdx = raw.indexOf(',')
    const firstCol = (commaIdx === -1 ? raw : raw.slice(0, commaIdx)).trim().replace(/^"(.*)"$/, '$1').trim()
    if (!firstCol) continue
    const lower = firstCol.toLowerCase()
    if (i === 0 && (lower === 'candidate_ref' || lower === 'candidateref')) continue
    refs.push(firstCol)
  }
  return refs
}

const escapeCsvCell = (value: string): string => `"${value.replaceAll('"', '""')}"`

/** Formats generated invite links as a downloadable CSV (`candidate_ref,link`). */
export function formatInvitesCsv(invites: CreatedInvite[]): string {
  const rows = ['candidate_ref,link']
  for (const inv of invites) {
    rows.push(`${escapeCsvCell(inv.candidateRef)},${escapeCsvCell(inv.link)}`)
  }
  return `${rows.join('\n')}\n`
}
