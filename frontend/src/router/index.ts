import { createRouter, createWebHistory } from 'vue-router'
import AssessmentView from '@/views/AssessmentView.vue'
import RankingView from '@/views/RankingView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/ranking',
    },
    {
      path: '/ranking',
      name: 'ranking',
      component: RankingView,
    },
    {
      path: '/assessment',
      name: 'assessment',
      component: AssessmentView,
    },
  ],
})

export default router
