<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import Message from 'primevue/message'
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { formatRatioPercent, formatScore, getCohortGaps } from '@/api/evaluation'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

const props = defineProps<{ configId: string }>()

const gapsQuery = useQuery({
  queryKey: computed(() => ['cohort-gaps', props.configId]),
  queryFn: () => getCohortGaps(props.configId),
  enabled: computed(() => Boolean(props.configId)),
})

const barOption = computed(() => {
  const skills = gapsQuery.data.value?.skills ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: skills.map((s) => s.skillName) },
    yAxis: { type: 'value', min: 0, max: 4, name: 'Mean Level' },
    series: [
      {
        name: 'SQL Mean Score',
        type: 'bar',
        data: skills.map((s) => s.meanScore ?? 0),
        itemStyle: { color: '#0c831f', borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
})
</script>

<template>
  <section class="space-y-4">
    <div>
      <h2 class="text-lg font-semibold text-ink">Cohort Gap Overview</h2>
      <p class="text-xs text-ink-muted">
        SQL-only statistics computed over released latest attempts, ordered weakest domain first.
      </p>
    </div>

    <div v-if="gapsQuery.isPending.value" class="rounded-lg border border-slate-200 bg-surface p-6 text-sm text-ink-muted">
      Loading cohort gaps...
    </div>

    <Message v-else-if="gapsQuery.isError.value" severity="error" :closable="false">
      Failed to load cohort gaps.
    </Message>

    <div
      v-else-if="!gapsQuery.data.value || gapsQuery.data.value.skills.length === 0"
      class="rounded-lg border border-slate-200 bg-surface p-6 text-sm text-ink-muted"
    >
      No released cohort data available yet.
    </div>

    <template v-else>
      <div class="rounded-xl border border-slate-200 bg-surface p-4">
        <p class="mb-2 text-xs text-ink-muted">
          Released candidates counted: <strong>{{ gapsQuery.data.value.releasedCandidateCount }}</strong>
        </p>
        <div class="h-56 w-full">
          <VChart :option="barOption" autoresize />
        </div>
      </div>

      <div class="overflow-x-auto rounded-xl border border-slate-200 bg-surface">
        <table class="min-w-full divide-y divide-slate-200 text-left text-sm">
          <thead class="bg-surface-sunken text-xs uppercase tracking-wide text-ink-muted">
            <tr>
              <th class="px-4 py-3">Skill</th>
              <th class="px-4 py-3">Mean Score</th>
              <th class="px-4 py-3">Scored</th>
              <th class="px-4 py-3">N/A Share</th>
              <th class="px-4 py-3">Distribution (L0–L4)</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="skill in gapsQuery.data.value.skills" :key="skill.skillId">
              <td class="px-4 py-3 font-medium text-ink">{{ skill.skillName }}</td>
              <td class="px-4 py-3 font-semibold tabular-nums text-ink">{{ formatScore(skill.meanScore) }}</td>
              <td class="px-4 py-3 tabular-nums text-ink">{{ skill.scoredCount }} / {{ skill.totalCandidates }}</td>
              <td class="px-4 py-3 tabular-nums text-ink-muted">{{ formatRatioPercent(skill.naShare) }}</td>
              <td class="px-4 py-3 font-mono text-xs text-ink-muted">
                [{{ skill.levelDistribution.join(', ') }}]
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
