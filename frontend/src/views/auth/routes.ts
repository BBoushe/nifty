import type { RouteRecordRaw } from 'vue-router'
import InviteLandingView from './InviteLandingView.vue'
import NotFoundView from './NotFoundView.vue'
import SignInView from './SignInView.vue'

// Owned by auth-accounts (US-AU-02) and invite-links (US-IL-03).
const routes: RouteRecordRaw[] = [
  { path: '/sign-in', name: 'sign-in', component: SignInView },
  { path: '/invite/:token', name: 'invite-landing', component: InviteLandingView },
  // REQ-NAV-10: vue-router ranks a catch-all below every other route, so it only gets unknown paths.
  { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView },
]

export default routes
