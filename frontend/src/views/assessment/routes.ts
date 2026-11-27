import type { RouteRecordRaw } from 'vue-router'
import AssessmentChatView from './AssessmentChatView.vue'
import ResultsView from './ResultsView.vue'

const routes: RouteRecordRaw[] = [
  { path: '/assessment', name: 'assessment', component: AssessmentChatView },
  { path: '/assessment/results/:configId', name: 'assessment-results', component: ResultsView },
]

export default routes
