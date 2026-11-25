import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiProblem, apiFetch, postSse } from './client'

const stubFetch = (response: Response) => {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const problem = (status: number, body: object) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json' } })

const sseResponse = (chunks: string[], contentType = 'text/event-stream') => {
  const encoder = new TextEncoder()
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
  return new Response(body, { headers: { 'Content-Type': contentType } })
}

const collect = async <T>(iterator: AsyncIterable<T>) => {
  const items: T[] = []
  for await (const item of iterator) items.push(item)
  return items
}

const rejection = (promise: Promise<unknown>) =>
  promise.then(
    () => {
      throw new Error('expected the promise to reject')
    },
    (error: ApiProblem) => error,
  )

afterEach(() => vi.unstubAllGlobals())

describe('apiFetch (REQ-FF-02)', () => {
  it('returns the parsed JSON body', async () => {
    stubFetch(new Response(JSON.stringify({ ok: true })))
    await expect(apiFetch<{ ok: boolean }>('/api/v1/x')).resolves.toEqual({ ok: true })
  })

  it('returns undefined for 204', async () => {
    stubFetch(new Response(null, { status: 204 }))
    await expect(apiFetch('/api/v1/x', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('throws an ApiProblem with 2 field errors on a 400 problem+json', async () => {
    const errors = [
      { field: 'questions[0].maxTimeSec', message: 'must be greater than minTimeSec', code: 'TIME_RANGE' },
      { field: 'title', message: 'must not be blank' },
    ]
    stubFetch(problem(400, { title: 'Bad Request', status: 400, detail: 'Invalid request content.', errors }))
    const error = await rejection(apiFetch('/api/v1/x'))
    expect(error).toBeInstanceOf(ApiProblem)
    expect(error).toMatchObject({ status: 400, title: 'Bad Request', detail: 'Invalid request content.', errors })
    expect(error.errors).toHaveLength(2)
  })

  it('throws an ApiProblem without errors for a non-problem failure', async () => {
    stubFetch(new Response('<html>bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }))
    const error = await rejection(apiFetch('/api/v1/x'))
    expect(error).toBeInstanceOf(ApiProblem)
    expect(error).toMatchObject({ status: 502, title: 'Bad Gateway', errors: [] })
  })
})

describe('postSse (REQ-FF-03)', () => {
  it('yields 3 events in order across chunk boundaries, then ends', async () => {
    const fetchMock = stubFetch(
      sseResponse([
        'event: token\ndata: {"t":"Hel',
        'lo"}\n\nevent: token\r\ndata: wor',
        'ld\n\n: keep-alive\n\nevent: done\ndata: line1\ndata: line2\n\n',
      ]),
    )
    const events = await collect(postSse('/api/v1/assessment/sessions', { a: 1 }))
    expect(events).toEqual([
      { event: 'token', data: '{"t":"Hello"}' },
      { event: 'token', data: 'world' },
      { event: 'done', data: 'line1\nline2' },
    ])
    const init = fetchMock.mock.calls[0][1]
    expect(init).toMatchObject({ method: 'POST', body: '{"a":1}' })
    expect(new Headers(init.headers).get('Accept')).toBe('text/event-stream')
  })

  it('names the event "message" when no event field is sent', async () => {
    stubFetch(sseResponse(['data: hi\n\n']))
    await expect(collect(postSse('/x'))).resolves.toEqual([{ event: 'message', data: 'hi' }])
  })

  it('throws an ApiProblem on a failed POST', async () => {
    stubFetch(problem(409, { title: 'Conflict', status: 409, detail: 'Maximum attempts reached.' }))
    await expect(collect(postSse('/x'))).rejects.toMatchObject({ status: 409, detail: 'Maximum attempts reached.' })
  })

  it('rejects a response that is not an event stream', async () => {
    stubFetch(sseResponse(['{}'], 'application/json'))
    await expect(collect(postSse('/x'))).rejects.toThrow('text/event-stream')
  })
})
