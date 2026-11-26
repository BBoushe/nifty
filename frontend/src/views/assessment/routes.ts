import type { RouteRecordRaw } from 'vue-router'
import AssessmentChatView from './AssessmentChatView.vue'

const routes: RouteRecordRaw[] = [{ path: '/assessment', name: 'assessment', component: AssessmentChatView }]

export default routes
