<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { startPageFor } from './guard'

// REQ-NAV-10: an unknown path ends here, with a way back to the page of the user's role.
const auth = useAuthStore()
const home = computed(() => startPageFor(auth.me?.role))
const homeLabel = computed(() =>
  auth.me?.role === 'PROFESSOR' ? 'Studio' : auth.me?.role === 'CANDIDATE' ? 'your interview' : 'sign in',
)
</script>

<template>
  <section class="mx-auto max-w-xl space-y-3 rounded-xl border border-slate-200 bg-white p-8 text-center">
    <h1 class="text-2xl font-bold text-slate-900">Page not found</h1>
    <p class="text-sm text-slate-600">This address does not match a page of this app.</p>
    <RouterLink
      :to="home"
      class="inline-block rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
      data-testid="not-found-home"
    >
      Go to {{ homeLabel }}
    </RouterLink>
  </section>
</template>
