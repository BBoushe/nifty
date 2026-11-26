<script setup lang="ts">
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import { computed } from 'vue'
import type { GroupingMode, SoftPreset } from '@/api/studio'
import { BIG_FIVE_TRAITS, useAssessmentFormStore } from '@/stores/assessmentForm'

const form = useAssessmentFormStore()

const groupingModes: Array<{ label: string; value: GroupingMode }> = [
  { label: 'Hard skills only', value: 'HARD_SKILLS' },
  { label: 'Soft skills only (BFI-10)', value: 'SOFT_SKILLS' },
  { label: 'Combined (hard + soft)', value: 'COMBINED' },
]

const softPresets: Array<{ label: string; value: SoftPreset }> = [
  { label: 'Engineering default', value: 'ENGINEERING_DEFAULT' },
  { label: 'Equal weights', value: 'EQUAL_WEIGHTS' },
  { label: 'Custom weights', value: 'CUSTOM' },
]

const softSkillsOnlyExempt = computed(
  () => form.settings.groupingMode === 'SOFT_SKILLS' && form.settings.bfi10Enabled,
)
// Decision 2.27: personality grouping needs BFI-10 data; the backend answers 400 BFI10_REQUIRED otherwise.
const bfi10Missing = computed(
  () => form.settings.groupingMode !== 'HARD_SKILLS' && !form.settings.bfi10Enabled,
)
</script>

<template>
  <section class="space-y-5 rounded-xl border border-slate-200 bg-white p-6">
    <div>
      <h2 class="text-lg font-semibold text-slate-900">1. Assessment Settings</h2>
      <p class="mt-1 text-sm text-slate-600">
        Settings are stored as an immutable snapshot once published. Ratios render as percentages here and
        are sent as 0–1 decimals.
      </p>
    </div>

    <div class="grid gap-4 md:grid-cols-2">
      <div class="flex flex-col gap-1.5 md:col-span-2">
        <label for="studio-title" class="text-sm font-medium text-slate-700">Title</label>
        <InputText
          id="studio-title"
          v-model="form.settings.title"
          data-testid="studio-title"
          placeholder="e.g., SE Block Week 2026"
          class="w-full"
        />
      </div>

      <div class="flex flex-col gap-1.5 md:col-span-2">
        <label for="studio-vision" class="text-sm font-medium text-slate-700">Assessment vision</label>
        <Textarea
          id="studio-vision"
          v-model="form.settings.vision"
          data-testid="studio-vision"
          rows="3"
          placeholder="Describe the course goal and interview focus for the rubric-blind interviewer."
          class="w-full"
        />
      </div>

      <div class="flex flex-col gap-1.5">
        <label for="studio-min-coverage" class="text-sm font-medium text-slate-700">
          Minimum coverage (%)
        </label>
        <InputNumber
          v-model="form.settings.minCoveragePercent"
          input-id="studio-min-coverage"
          :min="0"
          :max="100"
          suffix="%"
          fluid
        />
      </div>

      <div class="flex flex-col gap-1.5">
        <label for="studio-max-attempts" class="text-sm font-medium text-slate-700">Maximum attempts</label>
        <InputNumber v-model="form.settings.maxAttempts" input-id="studio-max-attempts" :min="1" fluid />
      </div>

      <div class="flex flex-col gap-1.5">
        <label for="studio-team-size" class="text-sm font-medium text-slate-700">Target team size</label>
        <InputNumber v-model="form.settings.teamSize" input-id="studio-team-size" :min="2" fluid />
      </div>

      <div class="flex flex-col gap-1.5">
        <label for="studio-grouping-mode" class="text-sm font-medium text-slate-700">Grouping mode</label>
        <Select
          v-model="form.settings.groupingMode"
          input-id="studio-grouping-mode"
          :options="groupingModes"
          option-label="label"
          option-value="value"
          class="w-full"
        />
      </div>

      <div class="flex flex-col gap-1.5">
        <label for="studio-soft-preset" class="text-sm font-medium text-slate-700">Soft-skill preset</label>
        <Select
          :model-value="form.settings.softPreset"
          input-id="studio-soft-preset"
          :options="softPresets"
          option-label="label"
          option-value="value"
          class="w-full"
          @update:model-value="(val: SoftPreset) => form.setSoftPreset(val)"
        />
      </div>

      <div
        v-if="form.settings.softPreset === 'CUSTOM'"
        class="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-4 md:col-span-2"
        data-testid="custom-trait-weights"
      >
        <p class="text-xs font-medium text-slate-700">
          Custom Big Five trait weights (each 0–1, total must equal 1.00)
        </p>
        <div class="grid gap-3 sm:grid-cols-5">
          <div v-for="trait in BIG_FIVE_TRAITS" :key="trait" class="flex flex-col gap-1">
            <label :for="`trait-weight-${trait}`" class="text-xs capitalize text-slate-600">{{ trait }}</label>
            <InputNumber
              v-model="form.settings.constraintWeights[trait]"
              :input-id="`trait-weight-${trait}`"
              :data-testid="`trait-weight-${trait}`"
              :min="0"
              :max="1"
              :step="0.05"
              :min-fraction-digits="2"
              :max-fraction-digits="2"
              fluid
            />
          </div>
        </div>
      </div>

      <div class="flex flex-col gap-1.5">
        <label for="studio-hard-weight" class="text-sm font-medium text-slate-700">
          Combined hard-skill share (%)
        </label>
        <InputNumber
          v-model="form.settings.combinedHardWeightPercent"
          input-id="studio-hard-weight"
          :min="0"
          :max="100"
          suffix="%"
          fluid
        />
      </div>

      <div class="flex flex-col gap-1.5">
        <label for="studio-alpha" class="text-sm font-medium text-slate-700">
          Team balance weight alpha (%)
        </label>
        <InputNumber
          v-model="form.settings.alphaPercent"
          input-id="studio-alpha"
          :min="0"
          :max="100"
          suffix="%"
          fluid
        />
      </div>
    </div>

    <div class="flex flex-wrap gap-6 pt-2">
      <label class="flex items-center gap-2 text-sm text-slate-700">
        <ToggleSwitch v-model="form.settings.inventedQuestions" />
        Allow interviewer-invented questions
      </label>

      <label class="flex items-center gap-2 text-sm text-slate-700">
        <ToggleSwitch v-model="form.settings.bfi10Enabled" />
        Enable BFI-10 personality step
      </label>
    </div>

    <Message v-if="bfi10Missing" severity="warn" data-testid="bfi10-required">
      Soft-skills and combined grouping need the BFI-10 personality step. Turn it on or choose hard-skills grouping.
    </Message>

    <Message v-if="softSkillsOnlyExempt" severity="info">
      SOFT_SKILLS mode with BFI-10 enabled allows 0 skills and 0 core questions.
    </Message>
  </section>
</template>
