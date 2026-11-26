import type { CandidateConversationTurn, TranscriptExportEntry } from '@/api/evaluation'

// REQ-SM-10, REQ-CR-07: one turn shape for the professor export and the candidate log,
// so one dialog renders both (coding standards, rule 6).
export interface TranscriptTurn {
  id: string
  turn: number
  origin: 'core' | 'follow_up' | string
  question: string
  askedAt: string
  answer: string | null
  submittedAt: string | null
  durationSec: number | null
  markers: { timedOut: boolean; pasteSuspected: boolean; injectionSuspected: boolean }
}

export interface TextSegment {
  text: string
  mark: boolean
}

// A blank answer is "no answer", never an empty bubble.
const answerOrNull = (text: string | null) => (text !== null && text.trim() !== '' ? text : null)

export function fromTranscriptExport(e: TranscriptExportEntry): TranscriptTurn {
  return {
    id: e.entryId,
    turn: e.turn,
    origin: e.question.origin,
    question: e.question.text,
    askedAt: e.question.askedAt,
    answer: answerOrNull(e.answer.text),
    submittedAt: e.answer.submittedAt,
    durationSec: e.answer.durationSec,
    markers: {
      timedOut: e.answer.timedOut,
      pasteSuspected: e.answer.pasteSuspected,
      injectionSuspected: e.answer.injectionSuspected,
    },
  }
}

// shortcut: the candidate log has no duration or paste data, so those stay empty; extend if the API adds them.
export function fromCandidateTurn(t: CandidateConversationTurn): TranscriptTurn {
  return {
    id: t.entryId,
    turn: t.seq,
    origin: t.origin,
    question: t.questionText,
    askedAt: t.askedAt,
    answer: answerOrNull(t.answerText),
    submittedAt: t.submittedAt,
    durationSec: null,
    markers: { timedOut: t.timedOut, pasteSuspected: false, injectionSuspected: false },
  }
}

export function formatDuration(sec: number | null): string {
  if (sec === null) return '—'
  if (sec < 60) return `${sec} s`
  const rest = sec % 60
  const min = Math.floor(sec / 60)
  return rest === 0 ? `${min} min` : `${min} min ${rest} s`
}

// HH:mm in the browser locale; an unparsable time shows nothing rather than "Invalid Date".
const clock = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
export function formatClock(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : clock.format(date)
}

// "Core question N" counts core questions only; follow-ups share the label.
export function originLabels(turns: TranscriptTurn[]): string[] {
  const labels: string[] = []
  let core = 0
  for (const t of turns) {
    if (t.origin === 'core') labels.push(`Core question ${++core}`)
    else if (t.origin === 'follow_up') labels.push('Follow-up')
    else labels.push(t.origin)
  }
  return labels
}

// Plain substring search, then merge overlaps. The output is text only, so the template never needs v-html.
// shortcut: only the first occurrence of each quote is marked; quotes are verified substrings, so one is enough.
export function splitHighlights(text: string, quotes: string[]): TextSegment[] {
  const ranges: Array<[number, number]> = []
  for (const q of quotes) {
    const at = q === '' ? -1 : text.indexOf(q)
    if (at >= 0) ranges.push([at, at + q.length])
  }
  ranges.sort((a, b) => a[0] - b[0])
  const segments: TextSegment[] = []
  let pos = 0
  let i = 0
  while (i < ranges.length) {
    const start = ranges[i][0]
    let end = ranges[i][1]
    for (i++; i < ranges.length && ranges[i][0] <= end; i++) end = Math.max(end, ranges[i][1])
    if (start > pos) segments.push({ text: text.slice(pos, start), mark: false })
    segments.push({ text: text.slice(start, end), mark: true })
    pos = end
  }
  if (pos < text.length || segments.length === 0) segments.push({ text: text.slice(pos), mark: false })
  return segments
}
