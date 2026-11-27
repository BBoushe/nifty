<script setup lang="ts">
import type { TeamCard } from '@/api/teams'

// REQ-TB-03, REQ-TB-07: renders backend-computed team numbers only. N/A is a hatched label, never 0 (REQ-TB-11).
defineProps<{ team: TeamCard; teamCount: number; locked: boolean }>()
const emit = defineEmits<{
  dragstart: [inviteId: string]
  drop: [teamNo: number]
  move: [inviteId: string, teamNo: number]
}>()

const onMove = (inviteId: string, event: Event) =>
  emit('move', inviteId, Number((event.target as HTMLSelectElement).value))
</script>

<template>
  <section
    class="rounded-xl border border-slate-200 bg-white p-4"
    :data-testid="`team-${team.teamNo}`"
    @dragover.prevent
    @drop.prevent="emit('drop', team.teamNo)"
  >
    <h2 class="text-base font-semibold text-slate-900">Team {{ team.teamNo }} · {{ team.size }} members</h2>

    <ul class="mt-3 space-y-2">
      <li
        v-for="m in team.members"
        :key="m.inviteId"
        class="rounded-lg bg-slate-50 px-3 py-2 text-sm"
        :class="locked ? '' : 'cursor-grab'"
        :draggable="!locked"
        :data-testid="`member-${m.inviteId}`"
        @dragstart="emit('dragstart', m.inviteId)"
      >
        <div class="flex items-center justify-between gap-2">
          <span class="font-medium text-slate-900">{{ m.candidateRef }}</span>
          <select
            class="rounded border border-slate-300 bg-white px-1 py-0.5 text-xs"
            :aria-label="`Move ${m.candidateRef} to team`"
            :value="team.teamNo"
            :disabled="locked"
            :data-testid="`move-${m.inviteId}`"
            @change="onMove(m.inviteId, $event)"
          >
            <option v-for="n in teamCount" :key="n" :value="n">Team {{ n }}</option>
          </select>
        </div>
        <ul class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-600">
          <li v-for="(score, skill) in m.skillScores" :key="skill">
            {{ skill }}:
            <span v-if="score === null" class="score-hatch rounded px-1 text-ink-muted" :data-testid="`score-${skill}`">N/A</span>
            <span v-else class="tabular-nums" :data-testid="`score-${skill}`">{{ score }}</span>
          </li>
        </ul>
        <p v-if="Object.keys(m.traits).length" class="mt-1 text-xs text-slate-500">
          Traits (professor only):
          <span v-for="(value, trait) in m.traits" :key="trait" class="mr-2">{{ trait }} {{ value }}</span>
        </p>
      </li>
    </ul>

    <dl class="mt-3 space-y-1 text-xs text-slate-700">
      <div v-for="(avg, skill) in team.skillAverages" :key="skill" class="flex justify-between gap-2">
        <dt>{{ skill }} average</dt>
        <dd class="tabular-nums" :data-testid="`avg-${skill}`">
          <span v-if="avg === null" class="score-hatch rounded px-1 text-ink-muted">N/A</span>
          <template v-else>{{ avg }}</template>
          <span class="ml-2 text-slate-500" :data-testid="`na-${skill}`">N/A count {{ team.skillNaCounts[skill] ?? 0 }}</span>
        </dd>
      </div>
      <div v-for="(avg, trait) in team.traitAverages" :key="trait" class="flex justify-between gap-2">
        <dt>{{ trait }} mean</dt>
        <dd class="tabular-nums" :data-testid="`trait-${trait}`">{{ avg ?? 'N/A' }}</dd>
      </div>
    </dl>
  </section>
</template>
