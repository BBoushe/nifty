<script setup lang="ts">
import Button from 'primevue/button'
import Message from 'primevue/message'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiProblem, type FieldError } from '@/api/client'
import { createAssessment } from '@/api/studio'
import { useAssessmentFormStore } from '@/stores/assessmentForm'

const form = useAssessmentFormStore()
const router = useRouter()

const submitting = ref(false)
const submitError = ref<string | null>(null)
const fieldErrors = ref<FieldError[]>([])

async function onSubmit() {
  submitError.value = null
  fieldErrors.value = []
  submitting.value = true
  try {
    const key = form.ensureIdempotencyKey()
    const created = await createAssessment(key, form.toApiPayload())
    form.reset()
    await router.push(`/ranking/studio/${created.configId}`)
  } catch (error) {
    if (error instanceof ApiProblem) {
      submitError.value = error.detail || error.title
      fieldErrors.value = error.errors
    } else {
      submitError.value = 'Could not publish the assessment. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
    <div>
      <h2 class="text-lg font-semibold text-slate-900">4. Review &amp; Publish</h2>
      <p class="mt-1 text-sm text-slate-600">
        Publishing saves an immutable v1 snapshot. You can review all fields before submitting once.
      </p>
    </div>

    <dl class="grid gap-3 rounded-lg bg-slate-50 p-4 text-sm sm:grid-cols-3">
      <div>
        <dt class="text-slate-500">Title</dt>
        <dd class="font-medium text-slate-900">{{ form.settings.title || '—' }}</dd>
      </div>
      <div>
        <dt class="text-slate-500">Grouping mode</dt>
        <dd class="font-medium text-slate-900">{{ form.settings.groupingMode }}</dd>
      </div>
      <div>
        <dt class="text-slate-500">Skills / Core questions</dt>
        <dd class="font-medium text-slate-900">{{ form.skills.length }} / {{ form.questions.length }}</dd>
      </div>
    </dl>

    <Message v-if="submitError" severity="error">
      <div class="space-y-2">
        <p class="font-medium">{{ submitError }}</p>
        <ul v-if="fieldErrors.length > 0" class="list-disc pl-5 text-sm">
          <li v-for="(err, idx) in fieldErrors" :key="`${err.field}-${idx}`">
            <code>{{ err.field }}</code>: {{ err.message }}
          </li>
        </ul>
      </div>
    </Message>

    <div class="flex justify-end gap-3">
      <Button
        label="Publish immutable v1"
        :loading="submitting"
        data-testid="submit-assessment"
        @click="onSubmit"
      />
    </div>
  </section>
</template>
