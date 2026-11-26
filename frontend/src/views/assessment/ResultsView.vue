<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { formatRatioPercent, formatScore, getCandidateResults } from '@/api/evaluation'
import ScoreBadge from '@/components/ScoreBadge.vue'

const route = useRoute()
const configId = computed(() => (typeof route.params.configId === 'string' ? route.params.configId : ''))

const resultsQuery = useQuery({
  queryKey: computed(() => ['candidate-results', configId.value]),
  queryFn: () => getCandidateResults(configId.value),
  enabled: computed(() => Boolean(configId.value)),
})
</script>

<template>
  <section class="mx-auto max-w-4xl space-y-6">
    <header class="rounded-xl border border-slate-200 bg-surface p-6 shadow-card">
      <p class="text-xs font-semibold uppercase tracking-wide text-ink-muted">Candidate Results</p>
      <h1 class="mt-1 text-2xl font-bold text-ink">Evidence-First Skill Profile</h1>
    </header>

    <div
      v-if="resultsQuery.isPending.value"
      class="rounded-xl border border-slate-200 bg-surface p-6 text-sm text-ink-muted"
    >
      Loading your assessment results...
    </div>

    <Message v-else-if="resultsQuery.isError.value" severity="error" :closable="false">
      Unable to load results for this assessment.
    </Message>

    <!-- REQ-CR-02: Pre-release gate withholds all scores, weights, formulas, and trait scores -->
    <div
      v-else-if="resultsQuery.data.value && !resultsQuery.data.value.released"
      class="rounded-xl border border-slate-200 bg-surface p-6 space-y-2"
      data-testid="results-pending"
    >
      <Tag value="Pending Review" severity="warn" />
      <h2 class="text-lg font-semibold text-ink">Your results are pending review</h2>
      <p class="text-sm text-ink-muted">
        Your assessment is currently being reviewed. Scores, evidence quotes, formula weights, and full
        transcript details will appear here once released.
      </p>
    </div>

    <!-- REQ-CR-01, REQ-CR-04..06: Post-release transparency -->
    <div
      v-else-if="resultsQuery.data.value && resultsQuery.data.value.released"
      class="space-y-6"
      data-testid="results-released"
    >
      <div class="grid gap-4 sm:grid-cols-3">
        <div class="rounded-xl border border-slate-200 bg-surface p-5">
          <p class="text-xs uppercase tracking-wide text-ink-muted">Fit Score (Unweighted Mean)</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-ink">
            {{ formatScore(resultsQuery.data.value.fitScore) }}
          </p>
        </div>
        <div class="rounded-xl border border-slate-200 bg-surface p-5">
          <p class="text-xs uppercase tracking-wide text-ink-muted">Question Coverage</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-ink">
            {{ formatRatioPercent(resultsQuery.data.value.coverage) }}
          </p>
        </div>
        <div class="rounded-xl border border-slate-200 bg-surface p-5">
          <p class="text-xs uppercase tracking-wide text-ink-muted">Formula Version</p>
          <p class="mt-2 text-sm font-semibold text-ink">
            {{ resultsQuery.data.value.formulaPreset }} · v{{ resultsQuery.data.value.formulaVersionNumber }}
          </p>
          <p class="mt-1 text-xs text-ink-muted">Status: {{ resultsQuery.data.value.releaseStatus }}</p>
        </div>
      </div>

      <!-- Skill Scores (explicit hatched N/A, never 0) -->
      <div class="rounded-xl border border-slate-200 bg-surface p-5 space-y-3">
        <h2 class="text-base font-semibold text-ink">Skill Scores</h2>
        <div class="grid gap-3 sm:grid-cols-2">
          <div
            v-for="skill in resultsQuery.data.value.skillScores ?? []"
            :key="skill.skillId"
            class="flex items-center justify-between rounded-lg border border-slate-200 px-4 py-3"
            :data-testid="`result-skill-${skill.skillId}`"
          >
            <div>
              <p class="font-medium text-ink">{{ skill.skillName }}</p>
              <p class="text-xs text-ink-muted">
                {{ skill.verifiedEvidenceCount }} verified quote(s)
                <span v-if="skill.avgConfidence !== null">
                  · confidence {{ formatRatioPercent(skill.avgConfidence) }}
                </span>
              </p>
            </div>
            <ScoreBadge v-if="skill.status === 'N/A' || skill.value === null" :level="null" />
            <span
              v-else
              class="inline-flex items-center rounded-pill bg-green-soft px-3 py-1 text-xs font-semibold tabular-nums text-ink"
            >
              {{ formatScore(skill.value) }} / 4.00
            </span>
          </div>
        </div>
      </div>

      <!-- REQ-CR-05: Deterministic Advice separating N/A from improvement areas -->
      <div v-if="resultsQuery.data.value.advice" class="rounded-xl border border-slate-200 bg-surface p-5 space-y-3">
        <h2 class="text-base font-semibold text-ink">Deterministic Summary</h2>
        <div class="grid gap-4 sm:grid-cols-3 text-sm">
          <div data-testid="advice-strengths">
            <h3 class="text-xs font-semibold uppercase tracking-wide text-brand-green">Strengths</h3>
            <ul class="mt-2 list-disc pl-5 text-ink">
              <li v-for="s in resultsQuery.data.value.advice.strengths" :key="s">{{ s }}</li>
              <li v-if="resultsQuery.data.value.advice.strengths.length === 0" class="list-none -ml-5 text-ink-muted">
                None highlighted yet.
              </li>
            </ul>
          </div>
          <div data-testid="advice-improvements">
            <h3 class="text-xs font-semibold uppercase tracking-wide text-warning-ink">Improvement Areas</h3>
            <ul class="mt-2 list-disc pl-5 text-ink">
              <li v-for="imp in resultsQuery.data.value.advice.improvements" :key="imp">{{ imp }}</li>
              <li v-if="resultsQuery.data.value.advice.improvements.length === 0" class="list-none -ml-5 text-ink-muted">
                None flagged.
              </li>
            </ul>
          </div>
          <div data-testid="advice-not-assessed">
            <h3 class="text-xs font-semibold uppercase tracking-wide text-ink-muted">Not Assessed (N/A)</h3>
            <ul class="mt-2 list-disc pl-5 text-ink-muted">
              <li v-for="na in resultsQuery.data.value.advice.notAssessed" :key="na">{{ na }}</li>
              <li v-if="resultsQuery.data.value.advice.notAssessed.length === 0" class="list-none -ml-5">
                All skills assessed.
              </li>
            </ul>
          </div>
        </div>
      </div>

      <!-- Verified Evidence Quotes -->
      <div class="rounded-xl border border-slate-200 bg-surface p-5 space-y-3">
        <h2 class="text-base font-semibold text-ink">Verified Transcript Evidence</h2>
        <ul class="space-y-3">
          <li
            v-for="ev in resultsQuery.data.value.evidence ?? []"
            :key="`${ev.entryId}-${ev.skillId}`"
            class="rounded-lg bg-surface-sunken p-3 text-sm space-y-1"
          >
            <div class="flex items-center justify-between">
              <span class="font-medium text-ink">{{ ev.skillName }}</span>
              <span class="text-xs text-ink-muted tabular-nums">
                Level {{ ev.level ?? 'N/A' }} · Weight {{ ev.questionWeight }} · Confidence
                {{ formatRatioPercent(ev.confidence) }}
              </span>
            </div>
            <blockquote v-if="ev.verifiedQuote" class="border-l-2 border-brand-green pl-3 text-xs text-ink">
              “{{ ev.verifiedQuote }}”
            </blockquote>
          </li>
        </ul>
      </div>

      <!-- REQ-CR-06: Own BFI-10 Trait Scores in neutral words (no ranking words) -->
      <div
        v-if="resultsQuery.data.value.softSkillTraits && resultsQuery.data.value.softSkillTraits.length > 0"
        class="rounded-xl border border-slate-200 bg-surface p-5 space-y-3"
        data-testid="bfi10-traits"
      >
        <h2 class="text-base font-semibold text-ink">Your Self-Reported Collaboration Style (BFI-10)</h2>
        <p class="text-xs text-ink-muted">
          Shown on a 1–5 scale for your own reflection only. Every profile has different strengths across team roles.
        </p>
        <div class="grid gap-3 sm:grid-cols-2">
          <div
            v-for="trait in resultsQuery.data.value.softSkillTraits"
            :key="trait.trait"
            class="rounded-lg border border-slate-200 p-3"
          >
            <div class="flex items-center justify-between">
              <span class="font-medium capitalize text-ink">{{ trait.trait }}</span>
              <span class="text-xs font-semibold tabular-nums text-ink">{{ trait.score.toFixed(1) }} / 5</span>
            </div>
            <p class="mt-1 text-xs text-ink-muted">{{ trait.description }}</p>
          </div>
        </div>
      </div>

      <!-- REQ-CR-04: Own Conversation Log -->
      <div class="rounded-xl border border-slate-200 bg-surface p-5 space-y-3">
        <h2 class="text-base font-semibold text-ink">Conversation Log</h2>
        <ol class="space-y-3">
          <li
            v-for="turn in resultsQuery.data.value.conversationLog ?? []"
            :key="turn.entryId"
            class="rounded-lg border border-slate-200 p-3 text-sm space-y-1"
          >
            <div class="flex items-center justify-between text-xs text-ink-muted">
              <span>Turn #{{ turn.seq }} ({{ turn.origin }})</span>
              <span>{{ turn.askedAt }}</span>
            </div>
            <p class="font-medium text-ink">Q: {{ turn.questionText }}</p>
            <p class="text-ink-muted">A: {{ turn.answerText || '(No answer submitted)' }}</p>
          </li>
        </ol>
      </div>
    </div>
  </section>
</template>
