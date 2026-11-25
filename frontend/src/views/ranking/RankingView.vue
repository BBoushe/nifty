<script setup lang="ts">
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { useAppStore } from '@/stores/app'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

const appStore = useAppStore()

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, top: 20, bottom: 32 },
  xAxis: {
    type: 'category',
    data: ['Git', 'CI/CD', 'Design', 'HitL AI', 'Java/Spring'],
  },
  yAxis: {
    type: 'value',
    max: 4,
    name: 'Level',
  },
  series: [
    {
      name: 'Sample Cohort Mean (Placeholder)',
      type: 'bar',
      data: [2.8, 2.1, 2.5, 3.0, 2.3],
      itemStyle: { color: '#0f172a', borderRadius: [4, 4, 0, 0] },
    },
  ],
}))

const modules = [
  { name: 'Interviewer Studio', spec: 'openspec/specs/interviewer-studio/spec.md', status: 'Seeded' },
  { name: 'Formula Engine', spec: 'openspec/specs/formula-engine/spec.md', status: 'Seeded' },
  { name: 'Skills Matrix & Review', spec: 'openspec/specs/skills-matrix-review/spec.md', status: 'Seeded' },
  { name: 'Team Builder', spec: 'openspec/specs/team-builder/spec.md', status: 'Seeded' },
]
</script>

<template>
  <section class="space-y-6">
    <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
      <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">
        Ranking View — Professor & Reviewer Control Plane
      </p>
      <h1 class="mt-1 text-2xl font-bold text-slate-900">{{ appStore.activeCohort }}</h1>
      <p class="mt-2 max-w-2xl text-sm text-slate-600">
        Placeholder shell wired with Vue 3.5, Pinia, Vue Router, TanStack Query, Tailwind CSS v4, and
        Apache ECharts. UI component library (PrimeVue vs. shadcn-vue) remains open in
        <code>docs/decisions.md</code> (O1).
      </p>
    </div>

    <div class="grid gap-4 md:grid-cols-2">
      <div class="rounded-xl border border-slate-200 bg-white p-5">
        <h2 class="text-base font-semibold text-slate-900">MVP Modules Ready for Implementation</h2>
        <ul class="mt-3 space-y-2 text-sm">
          <li
            v-for="mod in modules"
            :key="mod.name"
            class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2"
          >
            <div>
              <p class="font-medium text-slate-900">{{ mod.name }}</p>
              <p class="text-xs text-slate-500">{{ mod.spec }}</p>
            </div>
            <span class="rounded bg-slate-200 px-2 py-0.5 text-xs font-medium text-slate-700">
              {{ mod.status }}
            </span>
          </li>
        </ul>
      </div>

      <div class="rounded-xl border border-slate-200 bg-white p-5">
        <h2 class="text-base font-semibold text-slate-900">Cohort Overview (ECharts Smoke Chart)</h2>
        <p class="mt-1 text-xs text-slate-500">
          Synthetic placeholder verifying Apache ECharts integration. Real stats are SQL-only over approved scores.
        </p>
        <div class="mt-3 h-56 w-full">
          <VChart :option="chartOption" autoresize />
        </div>
      </div>
    </div>
  </section>
</template>
