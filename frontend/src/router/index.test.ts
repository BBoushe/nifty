import { describe, expect, it } from 'vitest'
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

  it('redirects / to /ranking', () => {
    expect(router.getRoutes().find((r) => r.path === '/')?.redirect).toBe('/ranking')
  })
})
