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

/** Extracts the first RFC-4180 CSV field from a single line, handling quoted commas and `""` escapes. */
function extractFirstCsvCell(line: string): string {
  const trimmed = line.trimStart()
  if (!trimmed.startsWith('"')) {
    const commaIdx = trimmed.indexOf(',')
    return (commaIdx === -1 ? trimmed : trimmed.slice(0, commaIdx)).trim()
  }
  let out = ''
  for (let i = 1; i < trimmed.length; i++) {
    const ch = trimmed[i]
    if (ch === '"') {
      if (trimmed[i + 1] === '"') {
        out += '"'
        i++
      } else {
        break
      }
    } else {
      out += ch
    }
  }
  return out.trim()
}

/** Parses a one-column CSV (`candidate_ref` header optional) into trimmed non-blank candidate refs. */
export function parseCandidateCsv(text: string): string[] {
  const cleaned = text.replace(/^\uFEFF/, '')
  const lines = cleaned.split(/\r?\n/)
  const refs: string[] = []
  let checkedHeader = false
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i].trim()
    if (!raw) continue
    const firstCol = extractFirstCsvCell(raw)
    if (!firstCol) continue
    if (!checkedHeader) {
      checkedHeader = true
      const lower = firstCol.toLowerCase()
      if (lower === 'candidate_ref' || lower === 'candidateref') continue
    }
    refs.push(firstCol)
  }
  return refs
}

const FORMULA_PREFIX = /^[=+\-@\t\r]/

const escapeCsvCell = (value: string): string => {
  const safe = FORMULA_PREFIX.test(value) ? `'${value}` : value
  return `"${safe.replaceAll('"', '""')}"`
}

/** Formats generated invite links as a downloadable CSV (`candidate_ref,link`). */
export function formatInvitesCsv(invites: CreatedInvite[]): string {
  const rows = ['candidate_ref,link']
  for (const inv of invites) {
    rows.push(`${escapeCsvCell(inv.candidateRef)},${escapeCsvCell(inv.link)}`)
  }
  return `${rows.join('\n')}\n`
}
