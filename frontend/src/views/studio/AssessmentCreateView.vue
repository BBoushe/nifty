<script setup lang="ts">
import { RouterLink } from 'vue-router'
import QuestionsSection from './QuestionsSection.vue'
import ReviewStep from './ReviewStep.vue'
import SettingsSection from './SettingsSection.vue'
import SkillsSection from './SkillsSection.vue'
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Create Assessment</h1>
        <p class="mt-1 text-sm text-slate-600">
          Your draft is saved in this browser automatically until you publish it once as v1.
        </p>
      </div>
      <RouterLink to="/ranking/studio" class="text-sm font-medium text-slate-700 underline">
        Back to assessments
      </RouterLink>
    </div>

    <SettingsSection />
    <SkillsSection />
    <QuestionsSection />
    <ReviewStep />
  </div>
</template>
