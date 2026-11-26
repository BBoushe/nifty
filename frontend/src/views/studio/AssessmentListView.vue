<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { RouterLink } from 'vue-router'
import { listAssessments } from '@/api/studio'

const { data, isPending, isError, error } = useQuery({
  queryKey: ['studio', 'assessments'],
  queryFn: listAssessments,
})
</script>

<template>
  <section class="space-y-6">
    <div class="flex items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Interviewer Studio</h1>
        <p class="mt-1 text-sm text-slate-600">
          Your published immutable assessment configurations, newest first.
        </p>
      </div>
      <RouterLink
        to="/ranking/studio/new"
        class="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
      >
        New assessment
      </RouterLink>
    </div>

    <div v-if="isPending" class="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
      Loading assessments…
    </div>

    <Message v-else-if="isError" severity="error">
      {{ error instanceof Error ? error.message : 'Failed to load assessments.' }}
    </Message>

    <div
      v-else-if="!data || data.length === 0"
      class="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-600"
    >
      No assessments created yet. Create your first assessment configuration to invite students.
    </div>

    <ul v-else class="space-y-3">
      <li
        v-for="item in data"
        :key="item.configId"
        class="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-5"
      >
        <div>
          <RouterLink
            :to="`/ranking/studio/${item.configId}`"
            class="text-base font-semibold text-slate-900 hover:underline"
          >
            {{ item.title }}
          </RouterLink>
          <p class="mt-1 text-xs text-slate-500">
            {{ item.skillCount }} skills · {{ item.questionCount }} core questions · team size
            {{ item.teamSize }} · {{ item.groupingMode }}
          </p>
        </div>
        <div class="flex items-center gap-2">
          <Tag :value="`${item.status} v${item.version}`" severity="secondary" />
        </div>
      </li>
    </ul>
  </section>
</template>
