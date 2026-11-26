// Shared API client (US-FF-04). Every feature calls the backend through these two functions.

export interface FieldError {
  field: string
  message: string
  code?: string
}

export interface SseEvent {
  event: string
  data: string
}

/** RFC 9457 ProblemDetail from docs/api.md §3 (REQ-FF-02). */
export class ApiProblem extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail: string,
    readonly errors: FieldError[] = [],
  ) {
    super(detail || title)
    this.name = 'ApiProblem'
  }
}

const isProblem = (response: Response) =>
  response.headers.get('Content-Type')?.includes('application/problem+json') ?? false

// HTTP/2 always sends an empty statusText, so fall back to the status code.
const statusTitle = (response: Response) => response.statusText || `HTTP ${response.status}`

async function toApiProblem(response: Response): Promise<ApiProblem> {
  if (isProblem(response)) {
    const body = await response.json().catch(() => ({}))
    return new ApiProblem(
      body.status ?? response.status,
      body.title ?? statusTitle(response),
      body.detail ?? '',
      Array.isArray(body.errors) ? body.errors : [],
    )
  }
  return new ApiProblem(response.status, statusTitle(response), '')
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

function readCookie(name: string): string | null {
  if (typeof document === 'undefined' || !document.cookie) return null
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) return decodeURIComponent(trimmed.slice(prefix.length))
  }
  return null
}

/** True when the browser holds an XSRF-TOKEN cookie (Decision 1.23). */
export const hasCsrfCookie = (): boolean => Boolean(readCookie('XSRF-TOKEN'))

// Decision 1.23: Spring Security's SPA CSRF reads X-XSRF-TOKEN on every state-changing request.
function withCsrfHeader(method: string | undefined, initHeaders?: HeadersInit): Headers {
  const headers = new Headers(initHeaders)
  if (!SAFE_METHODS.has((method ?? 'GET').toUpperCase()) && !headers.has('X-XSRF-TOKEN')) {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers.set('X-XSRF-TOKEN', token)
  }
  return headers
}

export async function apiFetch<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const requestInit = init ? { ...init, headers: withCsrfHeader(init.method, init.headers) } : undefined
  const response = await fetch(input, requestInit)
  if (!response.ok) throw await toApiProblem(response)
  // 204 or an empty 200/201 body has nothing to parse.
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Parses one SSE block (lines between blank lines). Returns null when it has no data field. */
function parseSseBlock(block: string): SseEvent | null {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    const value = colon === -1 ? '' : line.slice(line[colon + 1] === ' ' ? colon + 2 : colon + 1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }
  return data.length ? { event, data: data.join('\n') } : null
}

/** POST a JSON body and yield each SSE event in order until the stream closes (REQ-FF-03). */
export async function* postSse(url: string, body?: unknown, init: RequestInit = {}): AsyncGenerator<SseEvent> {
  const headers = withCsrfHeader('POST', init.headers)
  headers.set('Accept', 'text/event-stream')
  if (body !== undefined && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const response = await fetch(url, {
    ...init,
    method: 'POST',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw await toApiProblem(response)
  if (!response.headers.get('Content-Type')?.includes('text/event-stream') || !response.body) {
    throw new Error('Expected a text/event-stream response')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      // The \r\n replace runs on the whole buffer, so a \r\n split across two chunks still matches.
      buffer = (buffer + decoder.decode(value, { stream: !done })).replace(/\r\n/g, '\n')
      for (let end = buffer.indexOf('\n\n'); end !== -1; end = buffer.indexOf('\n\n')) {
        const parsed = parseSseBlock(buffer.slice(0, end))
        buffer = buffer.slice(end + 2)
        if (parsed) yield parsed
      }
      if (done) break
    }
    // SSE standard: a block without its closing blank line is incomplete, so it is dropped.
  } finally {
    await reader.cancel().catch(() => undefined)
  }
}
