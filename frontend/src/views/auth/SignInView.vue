<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiProblem } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

function safeRedirectTarget(): string {
  const redirect = route.query.redirect
  if (
    typeof redirect === 'string' &&
    redirect.startsWith('/') &&
    !redirect.startsWith('//') &&
    redirect !== '/assessment' &&
    !redirect.startsWith('/assessment/')
  ) {
    return redirect
  }
  return '/ranking'
}

async function onSubmit() {
  errorMessage.value = null
  submitting.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    await router.push(safeRedirectTarget())
  } catch (error) {
    if (error instanceof ApiProblem) {
      errorMessage.value = error.detail || error.title
    } else {
      errorMessage.value = 'Unable to sign in. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="mx-auto max-w-md py-12">
    <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
      <h1 class="text-xl font-semibold text-slate-900">Professor Sign In</h1>
      <p class="mt-1 text-sm text-slate-600">
        Sign in with your professor account to open the Ranking View.
      </p>

      <Message v-if="errorMessage" severity="error" class="mt-4">
        {{ errorMessage }}
      </Message>

      <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
        <div class="flex flex-col gap-1.5">
          <label for="sign-in-email" class="text-sm font-medium text-slate-700">Email</label>
          <InputText
            id="sign-in-email"
            v-model="email"
            type="email"
            autocomplete="username"
            required
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-1.5">
          <label for="sign-in-password" class="text-sm font-medium text-slate-700">Password</label>
          <InputText
            id="sign-in-password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            class="w-full"
          />
        </div>

        <Button type="submit" label="Sign in" :loading="submitting" class="w-full" />
      </form>
    </div>
  </section>
</template>
