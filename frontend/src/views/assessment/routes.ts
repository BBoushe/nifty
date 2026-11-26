import type { RouteRecordRaw } from 'vue-router'
import AssessmentView from './AssessmentView.vue'
import ResultsView from './ResultsView.vue'

const routes: RouteRecordRaw[] = [
  { path: '/assessment', name: 'assessment', component: AssessmentView },
  { path: '/assessment/results/:configId', name: 'assessment-results', component: ResultsView },
]

export default routes
