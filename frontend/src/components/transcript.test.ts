import { describe, expect, it } from 'vitest'
import type { CandidateConversationTurn, TranscriptExportEntry } from '@/api/evaluation'
import {
  formatDuration,
  fromCandidateTurn,
  fromTranscriptExport,
  originLabels,
  splitHighlights,
} from './transcript'

const exportEntry: TranscriptExportEntry = {
  entryId: 'e-1',
  turn: 3,
  question: {
    text: 'Explain transactions.',
    origin: 'follow_up',
    coreQuestionId: 'q-1',
    weight: 2,
    targetSkills: ['s1'],
    askedAt: '2026-11-26T10:03:00Z',
    expiresAt: null,
  },
  answer: {
    text: 'They roll back.',
    submittedAt: '2026-11-26T10:04:12Z',
    durationSec: 72,
    timedOut: false,
    pasteSuspected: true,
    injectionSuspected: false,
  },
  metadata: { promptedTerms: [], unpromptedTerms: [] },
}

describe('formatDuration', () => {
  it('formats seconds, minutes and missing values', () => {
    expect(formatDuration(48)).toBe('48 s')
    expect(formatDuration(72)).toBe('1 min 12 s')
    expect(formatDuration(120)).toBe('2 min')
    expect(formatDuration(null)).toBe('—')
  })
})

describe('transcript adapters', () => {
  it('maps a professor export entry', () => {
    expect(fromTranscriptExport(exportEntry)).toEqual({
      id: 'e-1',
      turn: 3,
      origin: 'follow_up',
      question: 'Explain transactions.',
      askedAt: '2026-11-26T10:03:00Z',
      answer: 'They roll back.',
      submittedAt: '2026-11-26T10:04:12Z',
      durationSec: 72,
      markers: { timedOut: false, pasteSuspected: true, injectionSuspected: false },
    })
  })

  it('maps a blank export answer to null, never to an empty string', () => {
    const blank = { ...exportEntry, answer: { ...exportEntry.answer, text: '  ' } }
    expect(fromTranscriptExport(blank).answer).toBeNull()
  })

  it('maps a candidate turn without paste or injection markers', () => {
    const turn: CandidateConversationTurn = {
      entryId: 'e-2',
      seq: 1,
      origin: 'core',
      questionText: 'Why tests?',
      answerText: null,
      askedAt: '2026-11-26T10:00:00Z',
      submittedAt: null,
      timedOut: true,
    }
    expect(fromCandidateTurn(turn)).toEqual({
      id: 'e-2',
      turn: 1,
      origin: 'core',
      question: 'Why tests?',
      askedAt: '2026-11-26T10:00:00Z',
      answer: null,
      submittedAt: null,
      durationSec: null,
      markers: { timedOut: true, pasteSuspected: false, injectionSuspected: false },
    })
  })
})

describe('originLabels', () => {
  it('numbers core questions and labels follow-ups', () => {
    const base = fromTranscriptExport(exportEntry)
    const turns = [
      { ...base, origin: 'core' },
      { ...base, origin: 'follow_up' },
      { ...base, origin: 'core' },
    ]
    expect(originLabels(turns)).toEqual(['Core question 1', 'Follow-up', 'Core question 2'])
  })
})

describe('splitHighlights', () => {
  it('returns one plain segment without quotes', () => {
    expect(splitHighlights('abc', [])).toEqual([{ text: 'abc', mark: false }])
  })

  it('splits around a quote and keeps markup as text', () => {
    expect(splitHighlights('I use <b>tests</b> daily', ['<b>tests</b>'])).toEqual([
      { text: 'I use ', mark: false },
      { text: '<b>tests</b>', mark: true },
      { text: ' daily', mark: false },
    ])
  })

  it('merges overlapping quotes and ignores quotes that are not found', () => {
    expect(splitHighlights('abcdef', ['abcd', 'cdef', 'zzz'])).toEqual([{ text: 'abcdef', mark: true }])
  })
})
