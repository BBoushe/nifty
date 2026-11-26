<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { onMounted } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { fetchBackendHealth } from '@/api/health'
import { useAuthStore } from '@/stores/auth'

const { data: health, isPending, isError } = useQuery({
  queryKey: ['backend-health'],
  queryFn: fetchBackendHealth,
  retry: 1,
})

const auth = useAuthStore()
const router = useRouter()
onMounted(() => auth.fetchMe())

async function signOut() {
  await auth.logout()
  await router.push('/sign-in')
}

const linkClass = 'rounded-md px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100'
const activeClass = 'bg-slate-900 text-white hover:bg-slate-900'
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <div class="flex items-center gap-6">
          <span class="text-lg font-semibold tracking-tight text-slate-900">
            Nifty
            <span class="ml-1 text-xs font-normal text-slate-500">Evidence-First Assessment</span>
          </span>
          <nav class="flex items-center gap-2">
            <template v-if="auth.me?.role === 'PROFESSOR'">
              <RouterLink to="/ranking/studio" :class="linkClass" :active-class="activeClass" data-testid="nav-studio">
                Studio
              </RouterLink>
              <RouterLink to="/ranking" exact-active-class="bg-slate-900 text-white hover:bg-slate-900" :class="linkClass" data-testid="nav-ranking">
                Ranking
              </RouterLink>
            </template>
            <template v-else-if="auth.me?.role === 'CANDIDATE'">
              <RouterLink to="/assessment" :class="linkClass" :active-class="activeClass" data-testid="nav-interview">
                Interview
              </RouterLink>
              <RouterLink
                :to="`/assessment/results/${auth.me.configId ?? ''}`"
                :class="linkClass"
                :active-class="activeClass"
                data-testid="nav-results"
              >
                Results
              </RouterLink>
            </template>
            <RouterLink v-else to="/sign-in" :class="linkClass" :active-class="activeClass" data-testid="nav-sign-in">
              Sign in
            </RouterLink>
          </nav>
        </div>

        <div class="flex items-center gap-3 text-xs">
          <button v-if="auth.me" type="button" :class="linkClass" data-testid="nav-sign-out" @click="signOut">
            Sign out
          </button>
          <span
            class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 font-medium"
            :class="
              health?.status === 'UP'
                ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200'
                : isPending
                  ? 'bg-amber-50 text-amber-700 ring-1 ring-amber-200'
                  : 'bg-rose-50 text-rose-700 ring-1 ring-rose-200'
            "
          >
            <span
              class="h-2 w-2 rounded-full"
              :class="
                health?.status === 'UP'
                  ? 'bg-emerald-500'
                  : isPending
                    ? 'bg-amber-500'
                    : 'bg-rose-500'
              "
            />
            <span v-if="health?.status === 'UP'">Backend UP ({{ health.service }})</span>
            <span v-else-if="isPending">Checking backend…</span>
            <span v-else-if="isError">Backend offline</span>
          </span>
        </div>
      </div>
    </header>

    <main class="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
      <RouterView />
    </main>
  </div>
</template>
