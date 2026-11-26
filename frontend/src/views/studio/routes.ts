import type { RouteRecordRaw } from 'vue-router'
import AssessmentCreateView from './AssessmentCreateView.vue'
import AssessmentDetailView from './AssessmentDetailView.vue'
import AssessmentListView from './AssessmentListView.vue'

// Owned by studio-assessment-creation (US-ST-06).
const routes: RouteRecordRaw[] = [
  { path: '/ranking/studio', name: 'studio-list', component: AssessmentListView },
  { path: '/ranking/studio/new', name: 'studio-create', component: AssessmentCreateView },
  { path: '/ranking/studio/:configId', name: 'studio-detail', component: AssessmentDetailView },
  { path: '/ranking/studio/:configId/invites', name: 'studio-invites', component: AssessmentDetailView },
]

export default routes
