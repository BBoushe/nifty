<script setup lang="ts">
const coreTopics = [
  'Git & Branching Workflows',
  'CI/CD Pipelines',
  'Design Thinking & Jira/Confluence',
  'Human-in-the-Loop AI Engineering',
  'Java 21 & Spring Boot',
]
</script>

<template>
  <section class="space-y-6">
    <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
      <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">
        Assessment View — Candidate Workspace
      </p>
      <h1 class="mt-1 text-2xl font-bold text-slate-900">Conversational Skill Assessment</h1>
      <p class="mt-2 max-w-2xl text-sm text-slate-600">
        Placeholder shell for <code>assessment-chat</code> (UX-09) and <code>candidate-results</code> (UX-10).
        No provisional scores, weights, or formulas are ever displayed during an active interview.
      </p>
    </div>

    <div class="grid gap-4 md:grid-cols-2">
      <div class="rounded-xl border border-slate-200 bg-white p-5">
        <h2 class="text-base font-semibold text-slate-900">Core Topics Progress (Stub)</h2>
        <ul class="mt-3 space-y-2 text-sm text-slate-700">
          <li
            v-for="(topic, idx) in coreTopics"
            :key="topic"
            class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2"
          >
            <span>{{ idx + 1 }}. {{ topic }}</span>
            <span class="rounded bg-slate-200 px-2 py-0.5 text-xs text-slate-600">Pending</span>
          </li>
        </ul>
      </div>

      <div class="rounded-xl border border-slate-200 bg-white p-5">
        <h2 class="text-base font-semibold text-slate-900">Post-Approval Results (Stub)</h2>
        <p class="mt-2 text-sm text-slate-600">
          Verified evidence quotes, skill levels (or explicit <code>N/A</code>), confidence scores,
          applied weights, and the formula version unlock here only after professor approval.
        </p>
      </div>
    </div>
  </section>
</template>
