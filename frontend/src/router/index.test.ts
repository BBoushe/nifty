import { describe, expect, it } from 'vitest'
import AssessmentView from '@/views/assessment/AssessmentView.vue'
import RankingView from '@/views/ranking/RankingView.vue'
import router from './index'

describe('router', () => {
  it.each([
    ['/ranking', 'ranking', RankingView],
    ['/assessment', 'assessment', AssessmentView],
  ])('keeps %s on the moved view', (path, name, view) => {
    const route = router.resolve(path)
    expect(route.name).toBe(name)
    expect(route.matched[0].components?.default).toBe(view)
  })

  it('redirects / to /ranking', () => {
    expect(router.getRoutes().find((r) => r.path === '/')?.redirect).toBe('/ranking')
  })
})
