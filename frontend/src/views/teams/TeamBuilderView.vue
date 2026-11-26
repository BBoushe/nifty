<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { BarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'
import { useRoute } from 'vue-router'
import {
  generateTeams,
  listTeams,
  publishTeams,
  recalculateTeams,
  type TeamProposal,
} from '@/api/teams'
import TeamCard from './TeamCard.vue'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent])

const route = useRoute()
const queryClient = useQueryClient()
const configId = computed(() => String(route.params.configId ?? ''))
const queryKey = computed(() => ['teams', configId.value])

const { data, isPending, isError, error } = useQuery({
  queryKey,
  queryFn: () => listTeams(configId.value),
  enabled: computed(() => configId.value.length > 0),
})

// The newest proposal is the one being edited.
const proposal = computed(() => data.value?.[0] ?? null)
const locked = computed(() => proposal.value?.status === 'PUBLISHED')

// Every mutation answers with the full proposal: store it, the cards re-render from backend numbers.
const store = (updated: TeamProposal, prepend = false) =>
  queryClient.setQueryData<TeamProposal[]>(queryKey.value, (old = []) =>
    prepend ? [updated, ...old] : old.map((p) => (p.proposalId === updated.proposalId ? updated : p)),
  )

const numTeams = ref<number | null>(null)
const mode = ref('')
const generate = useMutation({
  mutationFn: () =>
    generateTeams(configId.value, {
      numTeams: numTeams.value || undefined,
      groupingMode: (mode.value || undefined) as TeamProposal['groupingMode'] | undefined,
    }),
  onSuccess: (created) => store(created, true),
})
const recalculate = useMutation({
  mutationFn: (input: { proposalId: string; assignments: { inviteId: string; teamNo: number }[] }) =>
    recalculateTeams(input.proposalId, input.assignments),
  onSuccess: (updated) => store(updated),
})
const publish = useMutation({
  mutationFn: (proposalId: string) => publishTeams(proposalId),
  onSuccess: (updated) => store(updated),
})

const dragged = ref<string | null>(null)

function move(inviteId: string, teamNo: number) {
  const current = proposal.value
  if (!current || locked.value) return
  const assignments = current.teams.flatMap((team) =>
    team.members.map((m) => ({ inviteId: m.inviteId, teamNo: m.inviteId === inviteId ? teamNo : team.teamNo })),
  )
  if (current.teams.some((t) => t.teamNo === teamNo && t.members.some((m) => m.inviteId === inviteId))) return
  recalculate.mutate({ proposalId: current.proposalId, assignments })
}

function onDrop(teamNo: number) {
  if (dragged.value) move(dragged.value, teamNo)
  dragged.value = null
}

// Team Balance chart (rules: ECharts): one bar series per skill, one category per team; N/A stays a gap.
const chartOption = computed(() => {
  const teams = proposal.value?.teams ?? []
  const skills = teams.length ? Object.keys(teams[0].skillAverages) : []
  return {
    tooltip: { trigger: 'axis' },
    legend: {},
    grid: { left: 40, right: 20, top: 40, bottom: 32 },
    xAxis: { type: 'category', data: teams.map((t) => `Team ${t.teamNo}`) },
    yAxis: { type: 'value', min: 0, max: 4, name: 'Mean level' },
    series: skills.map((skill) => ({
      name: skill,
      type: 'bar',
      data: teams.map((t) => t.skillAverages[skill] ?? null),
    })),
  }
})

const errorText = (e: unknown, fallback: string) => (e instanceof Error && e.message ? e.message : fallback)
</script>

<template>
  <section class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Team Builder</h1>
        <p class="mt-1 text-sm text-slate-600">
          Seeded optimizer proposal. Drag a candidate to another team to recalculate. Nothing is final until you publish.
        </p>
      </div>
      <div class="flex flex-wrap items-end gap-2 text-sm">
        <label class="flex flex-col text-xs text-slate-600">
          Teams (optional)
          <input v-model.number="numTeams" type="number" min="1" class="w-24 rounded border border-slate-300 px-2 py-1">
        </label>
        <label class="flex flex-col text-xs text-slate-600">
          Mode
          <select v-model="mode" class="rounded border border-slate-300 px-2 py-1">
            <option value="">Assessment default</option>
            <option value="HARD_SKILLS">Hard skills</option>
            <option value="SOFT_SKILLS">Soft skills</option>
            <option value="COMBINED">Combined</option>
          </select>
        </label>
        <Button label="Generate teams" data-testid="generate" :loading="generate.isPending.value" @click="generate.mutate()" />
      </div>
    </div>

    <Message v-if="generate.isError.value" severity="error">
      {{ errorText(generate.error.value, 'Could not generate teams.') }}
    </Message>
    <Message v-if="recalculate.isError.value" severity="error">
      {{ errorText(recalculate.error.value, 'Could not recalculate the teams.') }}
    </Message>
    <Message v-if="publish.isError.value" severity="error">
      {{ errorText(publish.error.value, 'Could not publish the teams.') }}
    </Message>

    <div v-if="isPending" class="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
      Loading team proposals…
    </div>
    <Message v-else-if="isError" severity="error">
      {{ errorText(error, 'Could not load the team proposals.') }}
    </Message>
    <div v-else-if="!proposal" class="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-600">
      No team proposal yet. Generate one from the released scores.
    </div>

    <template v-else>
      <div class="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4">
        <div class="flex flex-wrap items-center gap-4 text-sm text-slate-700">
          <Tag :value="proposal.status" :severity="locked ? 'success' : 'secondary'" data-testid="status" />
          <span>Mode {{ proposal.groupingMode }} · seed {{ proposal.seed }}</span>
          <span>Imbalance <strong data-testid="diag-imbalance">{{ proposal.diagnostics.imbalance }}</strong></span>
          <span>Diversity <strong data-testid="diag-diversity">{{ proposal.diagnostics.diversity }}</strong></span>
          <span>N/A scores <strong data-testid="diag-na">{{ proposal.diagnostics.naCount }}</strong></span>
        </div>
        <Button
          v-if="!locked"
          label="Publish teams"
          data-testid="publish"
          :loading="publish.isPending.value"
          @click="publish.mutate(proposal.proposalId)"
        />
      </div>

      <p v-if="proposal.pendingInviteIds.length" class="text-sm text-slate-600" data-testid="pending">
        {{ proposal.pendingInviteIds.length }} candidate(s) wait for review and are not in any team.
      </p>

      <div class="rounded-xl border border-slate-200 bg-white p-4">
        <h2 class="text-base font-semibold text-slate-900">Team balance</h2>
        <div class="mt-3 h-64 w-full">
          <VChart :option="chartOption" autoresize />
        </div>
      </div>

      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <TeamCard
          v-for="team in proposal.teams"
          :key="team.teamNo"
          :team="team"
          :team-count="proposal.numTeams"
          :locked="locked"
          @dragstart="dragged = $event"
          @drop="onDrop"
          @move="move"
        />
      </div>
    </template>
  </section>
</template>
