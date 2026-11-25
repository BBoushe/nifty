import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchBackendHealth } from './health'

afterEach(() => vi.unstubAllGlobals())

describe('fetchBackendHealth', () => {
  it('returns the health payload', async () => {
    const body = { status: 'UP', service: 'nifty', aiProviderMode: 'mock', timestamp: 't' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body))))
    await expect(fetchBackendHealth()).resolves.toEqual(body)
  })

  it('throws on a non-2xx answer', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 503 })))
    await expect(fetchBackendHealth()).rejects.toThrow()
  })
})
