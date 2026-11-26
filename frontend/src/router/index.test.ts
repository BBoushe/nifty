import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type AuthMe, useAuthStore } from '@/stores/auth'
import AssessmentChatView from '@/views/assessment/AssessmentChatView.vue'
import RankingView from '@/views/ranking/RankingView.vue'
import router from './index'

describe('router', () => {
  it.each([
    ['/ranking', 'ranking', RankingView],
    ['/assessment', 'assessment', AssessmentChatView],
  ])('keeps %s on the moved view', (path, name, view) => {
    const route = router.resolve(path)
    expect(route.name).toBe(name)
    expect(route.matched[0].components?.default).toBe(view)
  })

  describe('start page (REQ-NAV-04)', () => {
    beforeEach(() => {
      setActivePinia(createPinia())
    })

    it.each<[string, AuthMe | null, string]>([
      ['a professor', { role: 'PROFESSOR', professorId: 'p1' }, '/ranking/studio'],
      ['a candidate', { role: 'CANDIDATE', professorId: null, configId: 'c1' }, '/assessment'],
      ['a visitor', null, '/sign-in'],
    ])('sends %s from / to %s', async (_who, me, target) => {
      const auth = useAuthStore()
      vi.spyOn(auth, 'fetchMe').mockImplementation(async () => {
        auth.me = me
        auth.loaded = true
        return me
      })
      await router.push('/sign-in') // leave any previous target so the next push is a real navigation
      await router.push('/')
      expect(router.currentRoute.value.path).toBe(target)
    })
  })
})
