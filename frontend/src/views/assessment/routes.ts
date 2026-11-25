import type { RouteRecordRaw } from 'vue-router'
import AssessmentView from './AssessmentView.vue'

const routes: RouteRecordRaw[] = [{ path: '/assessment', name: 'assessment', component: AssessmentView }]

export default routes
