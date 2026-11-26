import type { RouteRecordRaw } from 'vue-router'
import SignInView from './SignInView.vue'

// Owned by auth-accounts (US-AU-02).
const routes: RouteRecordRaw[] = [{ path: '/sign-in', name: 'sign-in', component: SignInView }]

export default routes
