<script setup lang="ts">
import Message from 'primevue/message'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiProblem } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const errorMessage = ref<string | null>(null)

onMounted(async () => {
  const token = String(route.params.token ?? '').trim()
  if (!token) {
    loading.value = false
    errorMessage.value = 'This link is not valid.'
    return
  }
  try {
    await auth.loginWithInvite(token)
    await router.replace('/assessment')
  } catch (error) {
    if (error instanceof ApiProblem && error.status === 401) {
      errorMessage.value = 'This link is not valid.'
    } else {
      errorMessage.value = 'Unable to open this assessment right now. Please try again.'
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="mx-auto max-w-md py-12">
    <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
      <h1 class="text-xl font-semibold text-slate-900">Opening Assessment</h1>
      <p v-if="loading" class="mt-2 text-sm text-slate-600">Checking your personal invite link…</p>
      <Message v-else-if="errorMessage" severity="error" class="mt-4">
        {{ errorMessage }}
      </Message>
    </div>
  </section>
</template>
