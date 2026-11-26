import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiProblem, apiFetch, postJsonOrSse, postSse } from './client'

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

describe('apiFetch edge cases', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses the status code as title when statusText is empty (HTTP/2)', async () => {
    stubFetch(new Response('bad gateway', { status: 502, statusText: '' }))
    await expect(apiFetch('/x')).rejects.toMatchObject({ status: 502, title: 'HTTP 502' })
  })

  it('returns undefined for an empty 201 body', async () => {
    stubFetch(new Response('', { status: 201 }))
    await expect(apiFetch('/x')).resolves.toBeUndefined()
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

  it('drops a last block that has no closing blank line', async () => {
    stubFetch(sseResponse(['data: one\n\n', 'data: {"half":']))
    await expect(collect(postSse('/x'))).resolves.toEqual([{ event: 'message', data: 'one' }])
  })

  it('rejects a response that is not an event stream', async () => {
    stubFetch(sseResponse(['{}'], 'application/json'))
    await expect(collect(postSse('/x'))).rejects.toThrow('text/event-stream')
  })
})

describe('postJsonOrSse (assessment start, REQ-AC-05)', () => {
  it('returns the parsed body for a JSON answer', async () => {
    const fetchMock = stubFetch(
      new Response(JSON.stringify({ status: 'BFI10' }), { headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(postJsonOrSse('/api/v1/assessment/sessions')).resolves.toEqual({ json: { status: 'BFI10' } })
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Accept')).toBe('text/event-stream, application/json')
  })

  it('returns the events for an SSE answer', async () => {
    stubFetch(sseResponse(['event: delta\ndata: {"text":"Hi"}\n\n']))
    const result = await postJsonOrSse('/api/v1/assessment/sessions')
    if (!('events' in result)) throw new Error('expected events')
    await expect(collect(result.events)).resolves.toEqual([{ event: 'delta', data: '{"text":"Hi"}' }])
  })

  it('throws an ApiProblem on a 409', async () => {
    stubFetch(problem(409, { title: 'Conflict', status: 409, detail: 'You have used all attempts.' }))
    await expect(postJsonOrSse('/x')).rejects.toMatchObject({ status: 409 })
  })
})

describe('CSRF header (REQ-AA-06, Decision 1.23)', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    vi.unstubAllGlobals()
  })

  it('sends X-XSRF-TOKEN on POST from the XSRF-TOKEN cookie', async () => {
    document.cookie = 'XSRF-TOKEN=tok-123-abc; path=/'
    const fetchMock = stubFetch(new Response(null, { status: 204 }))

    await apiFetch('/api/v1/auth/logout', { method: 'POST' })

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('tok-123-abc')
  })

  it('does not send X-XSRF-TOKEN on GET', async () => {
    document.cookie = 'XSRF-TOKEN=tok-123-abc; path=/'
    const fetchMock = stubFetch(new Response(JSON.stringify({ ok: true })))

    await apiFetch('/api/v1/auth/me')

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBeNull()
  })

  it('sends X-XSRF-TOKEN in postSse from the XSRF-TOKEN cookie', async () => {
    document.cookie = 'XSRF-TOKEN=sse-tok-99; path=/'
    const fetchMock = stubFetch(sseResponse(['data: ok\n\n']))

    await collect(postSse('/api/v1/assessment/sessions', { a: 1 }))

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('sse-tok-99')
  })

  it.each(['PUT', 'PATCH', 'DELETE'])(
    'sends decoded X-XSRF-TOKEN from multiple cookies on %s',
    async (method) => {
      document.cookie = 'other=1; path=/'
      document.cookie = 'XSRF-TOKEN=abc%3D123; path=/'
      document.cookie = 'tail=2; path=/'
      const fetchMock = stubFetch(new Response(null, { status: 204 }))

      await apiFetch('/api/v1/x', { method })

      const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
      expect(headers.get('X-XSRF-TOKEN')).toBe('abc=123')
      document.cookie = 'other=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
      document.cookie = 'tail=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    },
  )
})
