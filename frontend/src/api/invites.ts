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

/**
 * Reads the first field of every RFC 4180 record (REQ-AA-12). A quoted field, in any column, may hold
 * commas, `""` escapes and line breaks. Hand-written on purpose: one column needs no CSV library.
 * shortcut: an unterminated quote runs to the end of the text, upgrade if the import needs error reports.
 */
function firstCsvCells(text: string): string[] {
  const cells: string[] = []
  let cell = ''
  let inQuotes = false
  let pastFirst = false // after the first comma of the record: read, but do not keep
  let fieldStart = true // only blanks since the field began, so a quote opens a quoted field
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (inQuotes) {
      if (ch !== '"') {
        if (!pastFirst) cell += ch
      } else if (text[i + 1] === '"') {
        if (!pastFirst) cell += '"'
        i++
      } else {
        inQuotes = false
      }
    } else if (ch === '"' && fieldStart) {
      inQuotes = true
      fieldStart = false
      if (!pastFirst) cell = ''
    } else if (ch === ',') {
      pastFirst = true
      fieldStart = true
    } else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++
      cells.push(cell.trim())
      cell = ''
      pastFirst = false
      fieldStart = true
    } else {
      if (!pastFirst) cell += ch
      if (ch.trim() !== '') fieldStart = false
    }
  }
  cells.push(cell.trim())
  return cells
}

/** Parses a one-column CSV (`candidate_ref` header optional) into trimmed non-blank candidate refs. */
export function parseCandidateCsv(text: string): string[] {
  const cells = firstCsvCells(text.replace(/^\uFEFF/, ''))
  const refs: string[] = []
  let checkedHeader = false
  for (let i = 0; i < cells.length; i++) {
    const firstCol = cells[i]
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
