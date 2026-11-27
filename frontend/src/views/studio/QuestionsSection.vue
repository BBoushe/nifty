<script setup lang="ts">
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import MultiSelect from 'primevue/multiselect'
import Textarea from 'primevue/textarea'
import { computed } from 'vue'
import { useAssessmentFormStore } from '@/stores/assessmentForm'

const form = useAssessmentFormStore()

const skillOptions = computed(() =>
  form.skills
    .map((s) => s.name.trim())
    .filter((name) => name.length > 0),
)
</script>

<template>
  <section class="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
    <div class="flex items-center justify-between gap-4">
      <div>
        <h2 class="text-lg font-semibold text-slate-900">3. Ordered Core Questions</h2>
        <p class="mt-1 text-sm text-slate-600">
          Set question weights, confidence thresholds, min/max timers, follow-up limits (0–3), and target
          skills.
        </p>
      </div>
      <Button label="Add question" size="small" data-testid="add-question" @click="form.addQuestion()" />
    </div>

    <p v-if="form.questions.length === 0" class="rounded-lg bg-slate-50 p-4 text-sm text-slate-600">
      No core questions added yet.
    </p>

    <div
      v-for="(question, idx) in form.questions"
      :key="question.id"
      class="space-y-4 rounded-lg border border-slate-200 p-4"
    >
      <div class="flex items-center justify-between gap-2">
        <span class="text-sm font-semibold text-slate-800">Question {{ idx + 1 }}</span>
        <div class="flex items-center gap-1">
          <Button
            label="Up"
            size="small"
            text
            :disabled="idx === 0"
            :data-testid="`move-question-up-${idx}`"
            @click="form.moveQuestion(idx, -1)"
          />
          <Button
            label="Down"
            size="small"
            text
            :disabled="idx === form.questions.length - 1"
            :data-testid="`move-question-down-${idx}`"
            @click="form.moveQuestion(idx, 1)"
          />
          <Button
            label="Remove"
            severity="danger"
            size="small"
            text
            :data-testid="`remove-question-${idx}`"
            @click="form.removeQuestion(idx)"
          />
        </div>
      </div>

      <div class="flex flex-col gap-1.5">
        <label :for="`question-text-${idx}`" class="text-sm font-medium text-slate-700">Question text</label>
        <Textarea :id="`question-text-${idx}`" v-model="question.text" rows="2" class="w-full" />
      </div>

      <div class="flex flex-col gap-1.5">
        <label :for="`question-skills-${idx}`" class="text-sm font-medium text-slate-700">
          Target skills
        </label>
        <MultiSelect
          v-model="question.targetSkillNames"
          :input-id="`question-skills-${idx}`"
          :options="skillOptions"
          placeholder="Select target skills"
          class="w-full"
        />
        <p v-if="skillOptions.length > 0" class="text-xs text-slate-500">
          Available skills: {{ skillOptions.join(', ') }}
        </p>
      </div>

      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <div class="flex flex-col gap-1">
          <label :for="`q-weight-${idx}`" class="text-xs font-medium text-slate-600">Weight</label>
          <InputNumber
            v-model="question.weight"
            :input-id="`q-weight-${idx}`"
            :min="0.1"
            :max-fraction-digits="2"
            fluid
          />
        </div>

        <div class="flex flex-col gap-1">
          <label :for="`q-conf-${idx}`" class="text-xs font-medium text-slate-600">
            Confidence threshold (%)
          </label>
          <InputNumber
            v-model="question.confidenceThresholdPercent"
            :input-id="`q-conf-${idx}`"
            :min="0"
            :max="100"
            suffix="%"
            fluid
          />
        </div>

        <div class="flex flex-col gap-1">
          <label :for="`q-min-time-${idx}`" class="text-xs font-medium text-slate-600">Min time (s)</label>
          <InputNumber v-model="question.minTimeSec" :input-id="`q-min-time-${idx}`" :min="1" fluid />
        </div>

        <div class="flex flex-col gap-1">
          <label :for="`q-max-time-${idx}`" class="text-xs font-medium text-slate-600">Max time (s)</label>
          <InputNumber v-model="question.maxTimeSec" :input-id="`q-max-time-${idx}`" :min="2" fluid />
        </div>

        <div class="flex flex-col gap-1">
          <label :for="`q-followups-${idx}`" class="text-xs font-medium text-slate-600">
            Follow-up limit (0–3)
          </label>
          <InputNumber
            v-model="question.followUpLimit"
            :input-id="`q-followups-${idx}`"
            :min="0"
            :max="3"
            fluid
          />
        </div>
      </div>
    </div>
  </section>
</template>
