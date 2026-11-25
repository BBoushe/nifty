import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './auth'

const clearCsrfCookie = () => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

const jsonResponse = (body: object, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const problemResponse = (status: number, detail: string) =>
  new Response(JSON.stringify({ status, title: `HTTP ${status}`, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

describe('useAuthStore (REQ-AA-02, REQ-AA-06, Decision 1.23)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearCsrfCookie()
  })

  afterEach(() => {
    clearCsrfCookie()
    vi.unstubAllGlobals()
  })

  it('keeps loaded=false on a non-401 error so subsequent checks can retry', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(problemResponse(500, 'Server error'))
    vi.stubGlobal('fetch', fetchMock)

    const auth = useAuthStore()
    await expect(auth.fetchMe()).rejects.toMatchObject({ status: 500 })
    expect(auth.loaded).toBe(false)
    expect(auth.me).toBeNull()

    fetchMock.mockResolvedValueOnce(jsonResponse({ role: 'PROFESSOR', professorId: 'prof-1' }))
    await expect(auth.fetchMe()).resolves.toEqual({ role: 'PROFESSOR', professorId: 'prof-1' })
    expect(auth.loaded).toBe(true)
  })

  it('refreshes CSRF cookie before re-login after logout clears XSRF-TOKEN', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/auth/me') {
        if (!document.cookie.includes('XSRF-TOKEN=')) {
          document.cookie = 'XSRF-TOKEN=fresh-csrf-token; path=/'
          return problemResponse(401, 'Sign in to use this resource.')
        }
        return jsonResponse({ role: 'PROFESSOR', professorId: 'prof-1' })
      }
      if (url === '/api/v1/auth/login') {
        expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('fresh-csrf-token')
        return new Response(null, { status: 204 })
      }
      if (url === '/api/v1/auth/logout') {
        expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('fresh-csrf-token')
        clearCsrfCookie()
        return new Response(null, { status: 204 })
      }
      throw new Error(`Unexpected URL: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    const auth = useAuthStore()
    await auth.login('prof@example.test', 'synthetic-pass')
    expect(auth.me).toEqual({ role: 'PROFESSOR', professorId: 'prof-1' })

    await auth.logout()
    expect(auth.me).toBeNull()
    expect(auth.loaded).toBe(false)

    // Re-login without page reload must re-fetch /me to obtain a fresh XSRF-TOKEN before POST /login.
    await auth.login('prof@example.test', 'synthetic-pass')
    expect(auth.me).toEqual({ role: 'PROFESSOR', professorId: 'prof-1' })
  })
})
