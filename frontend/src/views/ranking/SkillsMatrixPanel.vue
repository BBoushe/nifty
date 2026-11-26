<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { HeatmapChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, VisualMapComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'
import {
  formatRatioPercent,
  formatScore,
  getSkillsMatrix,
  isEvaluationPending,
  POLL_INTERVAL_MS,
  type MatrixCell,
} from '@/api/evaluation'

use([CanvasRenderer, HeatmapChart, GridComponent, TooltipComponent, VisualMapComponent])

const props = defineProps<{ configId: string }>()
const emit = defineEmits<{ selectAttempt: [attemptId: string] }>()

const needsAttentionOnly = ref(false)

const matrixQuery = useQuery({
  queryKey: computed(() => ['evaluation-matrix', props.configId]),
  queryFn: () => getSkillsMatrix(props.configId),
  enabled: computed(() => Boolean(props.configId)),
  // REQ-SM-11: poll while any attempt still waits for the evaluator.
  refetchInterval: (query) =>
    query.state.data?.rows.some((row) => isEvaluationPending(row.evaluationStatus)) ? POLL_INTERVAL_MS : false,
})

const visibleRows = computed(() => {
  const rows = matrixQuery.data.value?.rows ?? []
  if (!needsAttentionOnly.value) return rows
  return rows.filter((row) => row.releaseStatus === 'flagged' || row.releaseStatus === 'pending' || row.flaggedCount > 0)
})

const heatmapOption = computed(() => {
  const data = matrixQuery.data.value
  if (!data || data.skills.length === 0 || visibleRows.value.length === 0) {
    return {}
  }
  const skillNames = data.skills.map((s) => s.skillName)
  const candidateLabels = visibleRows.value.map((r) => `${r.inviteId.slice(0, 8)} (#${r.attemptNo})`)
  const points: Array<{
    value: [number, number, number | null]
    itemStyle?: Record<string, unknown>
  }> = []

  visibleRows.value.forEach((row, yIdx) => {
    row.cells.forEach((cell, xIdx) => {
      const isNa = cell.status === 'N/A' || cell.value === null
      const isFlagged = cell.reviewStatus === 'flagged'
      points.push({
        value: [xIdx, yIdx, isNa ? null : cell.value],
        itemStyle: isNa
          ? {
              color: '#f3f4f6',
              decal: { symbol: 'rect', dashArrayX: [1, 0], dashArrayY: [2, 5], rotation: -Math.PI / 4 },
              borderColor: isFlagged ? '#b3261e' : '#d1d5db',
              borderWidth: isFlagged ? 2 : 1,
            }
          : {
              borderColor: isFlagged ? '#b3261e' : '#ffffff',
              borderWidth: isFlagged ? 2 : 1,
            },
      })
    })
  })

  return {
    tooltip: { position: 'top' },
    grid: { left: 120, right: 24, top: 16, bottom: 48 },
    xAxis: { type: 'category', data: skillNames, splitArea: { show: true } },
    yAxis: { type: 'category', data: candidateLabels, splitArea: { show: true } },
    visualMap: {
      min: 0,
      max: 4,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: ['#fbe4e2', '#fff3c4', '#ddf3e1', '#0c831f'] },
    },
    series: [
      {
        name: 'Skill Level',
        type: 'heatmap',
        data: points,
        label: {
          show: true,
          formatter: (params: { value: [number, number, number | null] }) =>
            params.value[2] === null ? 'N/A' : Number(params.value[2]).toFixed(2),
        },
      },
    ],
  }
})

function cellClasses(cell: MatrixCell): string[] {
  const classes: string[] = ['rounded-md', 'px-2', 'py-1', 'text-xs', 'font-semibold', 'tabular-nums', 'border']
  if (cell.status === 'N/A' || cell.value === null) {
    classes.push('score-hatch', 'bg-surface-sunken', 'text-ink-muted')
  } else if (cell.value >= 3.5) {
    classes.push('bg-brand-green', 'text-on-green')
  } else if (cell.value >= 2.5) {
    classes.push('bg-green-soft', 'text-ink')
  } else if (cell.value >= 1.5) {
    classes.push('bg-brand-yellow-soft', 'text-warning-ink')
  } else {
    classes.push('bg-danger-soft', 'text-danger')
  }
  if (cell.reviewStatus === 'flagged') {
    classes.push('border-danger', 'border-2')
  } else {
    classes.push('border-transparent')
  }
  return classes
}

function statusSeverity(status: string): 'success' | 'warn' | 'danger' | 'info' {
  if (status === 'flagged') return 'danger'
  if (status === 'pending') return 'warn'
  if (status === 'overridden') return 'info'
  return 'success'
}
</script>

<template>
  <section class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold text-ink">Skills Matrix &amp; Review Queue</h2>
        <p class="text-xs text-ink-muted">
          Backend-calculated Formula B scores. N/A cells are hatched and excluded from fit.
        </p>
      </div>
      <!-- Decision 2.30: professors review and approve every student attempt individually (no bulk approval). -->
      <div class="flex flex-wrap items-center gap-2">
        <Button
          label="Refresh"
          severity="secondary"
          size="small"
          :loading="matrixQuery.isFetching.value"
          data-testid="matrix-refresh-btn"
          @click="matrixQuery.refetch()"
        />
        <Button
          :label="needsAttentionOnly ? 'Showing Needs Attention' : 'Filter: Needs Attention'"
          :severity="needsAttentionOnly ? 'warn' : 'secondary'"
          size="small"
          data-testid="filter-needs-attention"
          @click="needsAttentionOnly = !needsAttentionOnly"
        />
      </div>
    </div>

    <div v-if="matrixQuery.isPending.value" class="rounded-lg border border-slate-200 bg-surface p-6 text-sm text-ink-muted">
      Loading skills matrix...
    </div>

    <Message v-else-if="matrixQuery.isError.value" severity="error" :closable="false">
      Failed to load skills matrix.
    </Message>

    <div
      v-else-if="!matrixQuery.data.value || visibleRows.length === 0"
      class="rounded-lg border border-slate-200 bg-surface p-6 text-sm text-ink-muted"
    >
      No completed candidate evaluations match the active filter yet.
    </div>

    <template v-else>
      <div class="rounded-xl border border-slate-200 bg-surface p-4">
        <div class="mb-2 flex items-center justify-between text-xs text-ink-muted">
          <span>Formula: {{ matrixQuery.data.value.formulaPreset }} (v{{ matrixQuery.data.value.formulaVersionNumber }})</span>
          <span>Min coverage gate: {{ formatRatioPercent(matrixQuery.data.value.minCoverage) }}</span>
        </div>
        <div class="h-64 w-full">
          <VChart :option="heatmapOption" autoresize />
        </div>
      </div>

      <div class="overflow-x-auto rounded-xl border border-slate-200 bg-surface">
        <table class="min-w-full divide-y divide-slate-200 text-left text-sm">
          <thead class="bg-surface-sunken text-xs uppercase tracking-wide text-ink-muted">
            <tr>
              <th class="px-4 py-3">Candidate</th>
              <th class="px-4 py-3">Status</th>
              <th class="px-4 py-3">Fit</th>
              <th class="px-4 py-3">Coverage</th>
              <th v-for="skill in matrixQuery.data.value.skills" :key="skill.skillId" class="px-3 py-3">
                {{ skill.skillName }}
              </th>
              <th class="px-4 py-3">Evidence</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="row in visibleRows" :key="row.attemptId" class="hover:bg-slate-50">
              <td class="px-4 py-3 font-mono text-xs text-ink">
                {{ row.inviteId.slice(0, 8) }} <span class="text-ink-muted">(#{{ row.attemptNo }})</span>
              </td>
              <td class="px-4 py-3">
                <Tag
                  v-if="row.evaluationStatus !== 'COMPLETED'"
                  :value="row.evaluationStatus === 'FAILED' ? 'evaluation failed' : 'evaluating'"
                  :severity="row.evaluationStatus === 'FAILED' ? 'danger' : 'secondary'"
                  :data-testid="`evaluation-status-${row.attemptId}`"
                />
                <Tag v-else :value="row.releaseStatus" :severity="statusSeverity(row.releaseStatus)" />
                <span v-if="row.flaggedCount > 0" class="ml-1 text-xs font-semibold text-danger">
                  ({{ row.flaggedCount }} flagged)
                </span>
              </td>
              <td class="px-4 py-3 font-semibold tabular-nums text-ink">
                {{ formatScore(row.fitScore) }}
              </td>
              <td class="px-4 py-3 tabular-nums text-ink">
                {{ formatRatioPercent(row.coverage) }}
              </td>
              <td v-for="cell in row.cells" :key="cell.skillId" class="px-3 py-3">
                <button
                  type="button"
                  :class="cellClasses(cell)"
                  :data-testid="`matrix-cell-${row.attemptId}-${cell.skillId}`"
                  @click="emit('selectAttempt', row.attemptId)"
                >
                  {{ cell.status === 'N/A' || cell.value === null ? 'N/A' : formatScore(cell.value) }}
                </button>
              </td>
              <td class="px-4 py-3">
                <Button
                  label="Inspect"
                  size="small"
                  severity="secondary"
                  :data-testid="`inspect-attempt-${row.attemptId}`"
                  @click="emit('selectAttempt', row.attemptId)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
