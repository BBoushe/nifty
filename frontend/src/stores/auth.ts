import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ApiProblem, apiFetch, hasCsrfCookie } from '@/api/client'

export type UserRole = 'PROFESSOR' | 'CANDIDATE'

export interface AuthMe {
  role: UserRole
  professorId: string | null
}

/** Session state for both roles (Decision 1.23, US-AU-02). */
export const useAuthStore = defineStore('auth', () => {
  const me = ref<AuthMe | null>(null)
  const loaded = ref(false)

  async function fetchMe(force = false): Promise<AuthMe | null> {
    if (loaded.value && !force) return me.value
    try {
      me.value = await apiFetch<AuthMe>('/api/v1/auth/me')
      loaded.value = true
    } catch (error) {
      if (error instanceof ApiProblem && error.status === 401) {
        me.value = null
        loaded.value = true
      } else {
        throw error
      }
    }
    return me.value
  }

  async function login(email: string, password: string): Promise<void> {
    // Ensure a fresh XSRF-TOKEN cookie exists on cold load and after logout (CsrfLogoutHandler clears it).
    if (!loaded.value || !hasCsrfCookie()) await fetchMe(true)
    await apiFetch<void>('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
    await fetchMe(true)
  }

  async function logout(): Promise<void> {
    await apiFetch<void>('/api/v1/auth/logout', { method: 'POST' })
    me.value = null
    // Spring Security's CsrfLogoutHandler clears XSRF-TOKEN; mark unloaded so the next login refreshes it.
    loaded.value = false
  }

  return { me, loaded, fetchMe, login, logout }
})
