import type { RouteRecordRaw } from 'vue-router'
import TeamBuilderView from './TeamBuilderView.vue'

// Owned by the team-builder change (US-TB-06).
const routes: RouteRecordRaw[] = [
  { path: '/ranking/teams/:configId', name: 'team-builder', component: TeamBuilderView },
]

export default routes
