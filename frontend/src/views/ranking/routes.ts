import type { RouteRecordRaw } from 'vue-router'
import RankingView from './RankingView.vue'

const routes: RouteRecordRaw[] = [{ path: '/ranking', name: 'ranking', component: RankingView }]

export default routes
