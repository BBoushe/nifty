<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { ref } from 'vue'
import { submitBfi10 } from '@/api/assessment'
import { ApiProblem } from '@/api/client'

// REQ-AC-14 / Decision 4.3: the 10 fixed BFI-10 items (Rammstedt & John, 2007). The backend scores them; this form
// only collects the answers and never shows a trait value.
const ITEMS = [
  'is reserved',
  'is generally trusting',
  'tends to be lazy',
  'is relaxed, handles stress well',
  'has few artistic interests',
  'is outgoing, sociable',
  'tends to find fault with others',
  'does a thorough job',
  'gets nervous easily',
  'has an active imagination',
]
const SCALE = [
  { value: 1, label: 'Disagree strongly' },
  { value: 2, label: 'Disagree a little' },
  { value: 3, label: 'Neither agree nor disagree' },
  { value: 4, label: 'Agree a little' },
  { value: 5, label: 'Agree strongly' },
]

const props = defineProps<{ attemptId: string }>()
const emit = defineEmits<{ submitted: [] }>()

const answers = ref<(number | null)[]>(ITEMS.map(() => null))
const error = ref<string | null>(null)

const { mutate, isPending } = useMutation({
  mutationFn: (items: number[]) => submitBfi10(props.attemptId, items),
  onSuccess: () => emit('submitted'),
  onError: (e) => {
    error.value =
      e instanceof ApiProblem && e.errors.length ? e.errors.map((f) => f.message).join(' ') : e.message
  },
})

function onSubmit() {
  const missing = answers.value.flatMap((answer, i) => (answer === null ? [i + 1] : []))
  if (missing.length) {
    error.value = `Answer every item. Missing: ${missing.join(', ')}.`
    return
  }
  error.value = null
  mutate(answers.value as number[])
}
</script>

<template>
  <form data-testid="bfi10-form" class="space-y-4 rounded-xl border border-slate-200 bg-white p-6" @submit.prevent="onSubmit">
    <div>
      <h2 class="text-lg font-semibold text-slate-900">Short personality questionnaire</h2>
      <p class="mt-1 text-sm text-slate-600">I see myself as someone who…</p>
    </div>

    <fieldset v-for="(item, i) in ITEMS" :key="item" class="rounded-lg bg-slate-50 p-3">
      <legend class="text-sm font-medium text-slate-800">{{ i + 1 }}. … {{ item }}</legend>
      <div class="mt-2 flex flex-wrap gap-3">
        <label v-for="option in SCALE" :key="option.value" class="flex items-center gap-1 text-xs text-slate-700">
          <input
            v-model="answers[i]"
            type="radio"
            :name="`bfi10-item-${i + 1}`"
            :value="option.value"
            :data-testid="`bfi10-item-${i + 1}-${option.value}`"
          >
          {{ option.label }}
        </label>
      </div>
    </fieldset>

    <Message v-if="error" severity="error" data-testid="bfi10-error">{{ error }}</Message>
    <Button type="submit" label="Submit questionnaire" :loading="isPending" />
  </form>
</template>
