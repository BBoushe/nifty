<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useAssessmentFormStore } from '@/stores/assessmentForm'

const form = useAssessmentFormStore()

const LEVEL_LABELS = [
  '0 — No evidence',
  '1 — Basic',
  '2 — Developing',
  '3 — Proficient',
  '4 — Expert',
]
</script>

<template>
  <section class="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
    <div class="flex items-center justify-between gap-4">
      <div>
        <h2 class="text-lg font-semibold text-slate-900">2. Skills &amp; Hidden Rubrics</h2>
        <p class="mt-1 text-sm text-slate-600">
          Define each skill with 5 ascending rubric levels (0–4). Rubrics are hidden from the interviewer
          and used only by the post-interview evaluator.
        </p>
      </div>
      <Button label="Add skill" size="small" data-testid="add-skill" @click="form.addSkill()" />
    </div>

    <p v-if="form.skills.length === 0" class="rounded-lg bg-slate-50 p-4 text-sm text-slate-600">
      No skills added yet. Add at least one skill unless running SOFT_SKILLS mode with BFI-10 enabled.
    </p>

    <div
      v-for="(skill, skillIndex) in form.skills"
      :key="skill.id"
      class="space-y-3 rounded-lg border border-slate-200 p-4"
    >
      <div class="flex items-end gap-3">
        <div class="flex flex-1 flex-col gap-1">
          <label :for="`skill-name-${skillIndex}`" class="text-sm font-medium text-slate-700">
            Skill name
          </label>
          <InputText
            :id="`skill-name-${skillIndex}`"
            :model-value="skill.name"
            placeholder="e.g., SQL Transactions"
            class="w-full"
            @update:model-value="(val) => form.renameSkill(skillIndex, val ?? '')"
          />
        </div>
        <Button
          label="Remove"
          severity="danger"
          text
          size="small"
          :data-testid="`remove-skill-${skillIndex}`"
          @click="form.removeSkill(skillIndex)"
        />
      </div>

      <div class="grid gap-3 md:grid-cols-5">
        <div v-for="(label, levelIndex) in LEVEL_LABELS" :key="label" class="flex flex-col gap-1">
          <label :for="`rubric-${skillIndex}-${levelIndex}`" class="text-xs font-medium text-slate-600">
            {{ label }}
          </label>
          <Textarea
            :id="`rubric-${skillIndex}-${levelIndex}`"
            v-model="skill.rubricLevels[levelIndex]"
            :data-testid="`rubric-level-${skillIndex}-${levelIndex}`"
            rows="3"
            class="w-full text-sm"
          />
        </div>
      </div>
    </div>
  </section>
</template>
