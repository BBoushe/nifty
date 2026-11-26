import { apiFetch, postJsonOrSse, postSse, type SseEvent } from './client'

// docs/api.md §4 and §4.1 (assessment-chat). The candidate never receives scores, weights or flags (REQ-AC-03).

export type AttemptStatus = 'IN_PROGRESS' | 'BFI10' | 'COMPLETED'

export interface SessionMessage {
  seq: number
  author: 'interviewer' | 'candidate'
  content: string
  createdAt: string
}

export interface SessionState {
  attemptId: string
  status: AttemptStatus
  attemptNo: number
  bfi10Enabled: boolean
  questionsRemaining: number
  expiresAt: string | null
  messages: SessionMessage[]
  /** Server clock when the state was read; the countdown corrects the browser clock with it. */
  serverTime?: string
}

export type InterviewEvent =
  | { type: 'delta'; text: string }
  | {
      type: 'question'
      attemptId: string
      entryId: string
      origin: 'core' | 'follow_up'
      expiresAt: string
      questionsRemaining: number
      serverTime?: string
    }
  | { type: 'done'; status: AttemptStatus }

/** Answer limit of the backend (REQ-AC-20, Decision 2.26). */
export const MAX_ANSWER_CHARS = 4000

const SESSIONS = '/api/v1/assessment/sessions'

async function* toInterviewEvents(events: AsyncIterable<SseEvent>): AsyncGenerator<InterviewEvent> {
  for await (const { event, data } of events) {
    if (event === 'delta' || event === 'question' || event === 'done') {
      yield { type: event, ...JSON.parse(data) } as InterviewEvent
    }
  }
}

export async function startSession(): Promise<{ state: SessionState } | { events: AsyncGenerator<InterviewEvent> }> {
  const result = await postJsonOrSse<SessionState>(SESSIONS)
  return 'json' in result ? { state: result.json } : { events: toInterviewEvents(result.events) }
}

export function sendAnswer(attemptId: string, content: string): AsyncGenerator<InterviewEvent> {
  return toInterviewEvents(postSse(`${SESSIONS}/${encodeURIComponent(attemptId)}/answers`, { content }))
}

export function getSession(attemptId: string): Promise<SessionState> {
  return apiFetch<SessionState>(`${SESSIONS}/${encodeURIComponent(attemptId)}`)
}

export function submitBfi10(attemptId: string, items: number[]): Promise<void> {
  return apiFetch<void>(`${SESSIONS}/${encodeURIComponent(attemptId)}/bfi10`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ items }),
  })
}
