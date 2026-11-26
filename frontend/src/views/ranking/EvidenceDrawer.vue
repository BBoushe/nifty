<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { computed, reactive, ref, watch } from 'vue'
import {
  approveEvaluation,
  formatRatioPercent,
  formatScore,
  getAttemptEvaluations,
  getTranscriptExport,
  overrideEvaluation,
  releaseAttempt,
} from '@/api/evaluation'
import ScoreBadge from '@/components/ScoreBadge.vue'

const props = defineProps<{ attemptId: string | null; visible: boolean }>()
const emit = defineEmits<{ close: []; updated: [] }>()

const queryClient = useQueryClient()
const overrideLevels = reactive<Record<string, number>>({})
const overrideReasons = reactive<Record<string, string>>({})
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

watch(
  () => props.attemptId,
  () => {
    actionError.value = null
    actionSuccess.value = null
  },
)

const drilldownQuery = useQuery({
  queryKey: computed(() => ['attempt-evaluations', props.attemptId]),
  queryFn: () => getAttemptEvaluations(props.attemptId!),
  enabled: computed(() => Boolean(props.visible && props.attemptId)),
})

// REQ-SM-08: load the full conversation ledger with timestamps, durationSec, and anomaly markers.
const transcriptQuery = useQuery({
  queryKey: computed(() => ['attempt-transcript', props.attemptId]),
  queryFn: () => getTranscriptExport(props.attemptId!),
  enabled: computed(() => Boolean(props.visible && props.attemptId)),
})

const isAlreadyReleased = computed(() => {
  const status = drilldownQuery.data.value?.releaseStatus
  return status === 'auto_released' || status === 'approved' || status === 'overridden'
})

const overrideMutation = useMutation({
  mutationFn: ({ responseEvalId, newLevel, reason }: { responseEvalId: string; newLevel: number; reason: string }) =>
    overrideEvaluation(responseEvalId, newLevel, reason),
  onSuccess: () => {
    actionError.value = null
    actionSuccess.value = 'Override saved, audited, and Formula B recalculated.'
    queryClient.invalidateQueries({ queryKey: ['attempt-evaluations', props.attemptId] })
    queryClient.invalidateQueries({ queryKey: ['attempt-transcript', props.attemptId] })
    queryClient.invalidateQueries({ queryKey: ['evaluation-matrix'] })
    queryClient.invalidateQueries({ queryKey: ['cohort-gaps'] })
    emit('updated')
  },
  onError: (err: Error) => {
    actionError.value = err.message || 'Override failed.'
  },
})

const approveMutation = useMutation({
  mutationFn: (responseEvalId: string) => approveEvaluation(responseEvalId),
  onSuccess: () => {
    actionError.value = null
    actionSuccess.value = 'Evaluation approved as-is and recorded in audit log.'
    queryClient.invalidateQueries({ queryKey: ['attempt-evaluations', props.attemptId] })
    queryClient.invalidateQueries({ queryKey: ['evaluation-matrix'] })
    queryClient.invalidateQueries({ queryKey: ['cohort-gaps'] })
    emit('updated')
  },
  onError: (err: Error) => {
    actionError.value = err.message || 'Approval failed.'
  },
})

const releaseMutation = useMutation({
  mutationFn: () => releaseAttempt(props.attemptId!),
  onSuccess: () => {
    actionError.value = null
    actionSuccess.value = 'Attempt approved and released to candidate.'
    queryClient.invalidateQueries({ queryKey: ['attempt-evaluations', props.attemptId] })
    queryClient.invalidateQueries({ queryKey: ['evaluation-matrix'] })
    queryClient.invalidateQueries({ queryKey: ['cohort-gaps'] })
    emit('updated')
  },
  onError: (err: Error) => {
    actionError.value = err.message || 'Release failed.'
  },
})

function submitOverride(responseEvalId: string, fallbackLevel: number | null) {
  const reason = (overrideReasons[responseEvalId] ?? '').trim()
  if (!reason) {
    actionError.value = 'Override reason is required.'
    return
  }
  const rawLevel = overrideLevels[responseEvalId] ?? fallbackLevel ?? 0
  overrideMutation.mutate({ responseEvalId, newLevel: Number(rawLevel), reason })
}

async function downloadTranscript() {
  if (!props.attemptId) return
  try {
    // REQ-SM-02, REQ-SM-08: always fetch fresh export so post-override evidence is never stale.
    const payload = await getTranscriptExport(props.attemptId)
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${props.attemptId}.transcript.json`
    link.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    actionError.value = err instanceof Error ? err.message : 'Failed to export transcript.'
  }
}
</script>

<template>
  <aside
    v-if="visible && attemptId"
    class="rounded-xl border border-slate-200 bg-surface p-5 shadow-card space-y-4"
    data-testid="evidence-drawer"
  >
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-3">
      <div>
        <h3 class="text-base font-semibold text-ink">Evidence Drawer</h3>
        <p class="font-mono text-xs text-ink-muted">Attempt {{ attemptId }}</p>
      </div>
      <div class="flex items-center gap-2">
        <Button label="Export Transcript" size="small" severity="secondary" @click="downloadTranscript" />
        <Button
          label="Release Attempt"
          size="small"
          :disabled="!drilldownQuery.data.value || drilldownQuery.data.value.flaggedCount > 0 || isAlreadyReleased"
          :loading="releaseMutation.isPending.value"
          data-testid="release-attempt-btn"
          @click="releaseMutation.mutate()"
        />
        <Button label="Close" size="small" severity="secondary" text @click="emit('close')" />
      </div>
    </div>

    <Message v-if="actionError" severity="error" :closable="false">{{ actionError }}</Message>
    <Message v-if="actionSuccess" severity="success" :closable="false">{{ actionSuccess }}</Message>

    <div v-if="drilldownQuery.isPending.value" class="text-sm text-ink-muted">Loading evidence...</div>
    <Message v-else-if="drilldownQuery.isError.value" severity="error" :closable="false">
      Could not load attempt evaluations.
    </Message>

    <template v-else-if="drilldownQuery.data.value">
      <div class="grid gap-3 sm:grid-cols-4 text-xs">
        <div class="rounded-lg bg-surface-sunken p-3">
          <div class="text-ink-muted">Fit Score</div>
          <div class="mt-1 text-base font-bold text-ink">{{ formatScore(drilldownQuery.data.value.fitScore) }}</div>
        </div>
        <div class="rounded-lg bg-surface-sunken p-3">
          <div class="text-ink-muted">Coverage</div>
          <div class="mt-1 text-base font-bold text-ink">
            {{ formatRatioPercent(drilldownQuery.data.value.coverage) }}
            <span class="text-xs font-normal text-ink-muted">
              (min {{ formatRatioPercent(drilldownQuery.data.value.minCoverage) }})
            </span>
          </div>
        </div>
        <div class="rounded-lg bg-surface-sunken p-3">
          <div class="text-ink-muted">Release Status</div>
          <div class="mt-1"><Tag :value="drilldownQuery.data.value.releaseStatus" /></div>
        </div>
        <div class="rounded-lg bg-surface-sunken p-3">
          <div class="text-ink-muted">Flagged Items</div>
          <div class="mt-1 text-base font-bold" :class="drilldownQuery.data.value.flaggedCount > 0 ? 'text-danger' : 'text-ink'">
            {{ drilldownQuery.data.value.flaggedCount }}
          </div>
        </div>
      </div>

      <div v-if="drilldownQuery.data.value.responses.length === 0" class="text-sm text-ink-muted">
        No evaluated responses recorded for this attempt.
      </div>

      <ul v-else class="space-y-3">
        <li
          v-for="item in drilldownQuery.data.value.responses"
          :key="item.responseEvaluationId"
          class="rounded-lg border p-4 space-y-3"
          :class="item.reviewStatus === 'flagged' ? 'border-danger bg-danger-soft/20' : 'border-slate-200'"
        >
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <span class="font-semibold text-ink">{{ item.skillName }}</span>
              <ScoreBadge :level="item.effectiveLevel" />
              <Tag :value="item.reviewStatus" :severity="item.reviewStatus === 'flagged' ? 'danger' : 'secondary'" />
            </div>
            <div class="text-xs text-ink-muted tabular-nums">
              Confidence: {{ formatRatioPercent(item.calculatedConfidence) }}
              <span v-if="item.confidenceThreshold !== null">
                / threshold {{ formatRatioPercent(item.confidenceThreshold) }}
              </span>
              · Judge runs: [{{ item.run1Level ?? '—' }}, {{ item.run2Level ?? '—' }}]
              · Weight: {{ item.questionWeight }}
            </div>
          </div>

          <div v-if="item.flagReasons.length > 0" class="flex flex-wrap gap-1">
            <Tag v-for="reason in item.flagReasons" :key="reason" :value="reason" severity="danger" />
          </div>

          <blockquote class="rounded-md bg-surface-sunken px-3 py-2 text-xs text-ink border-l-2 border-ai-blue">
            <span v-if="item.quoteVerified && item.verifiedQuote">“{{ item.verifiedQuote }}”</span>
            <span v-else class="italic text-ink-muted">No verified verbatim quote.</span>
          </blockquote>

          <p v-if="item.aiRationale" class="text-xs text-ink-muted">
            <span class="font-semibold text-ai-ink">AI rationale:</span> {{ item.aiRationale }}
          </p>

          <div v-if="item.overrideReason" class="text-xs text-ink">
            <strong>Override applied</strong> (AI level {{ item.aiLevel ?? 'N/A' }} → {{ item.effectiveLevel }}):
            {{ item.overrideReason }}
          </div>

          <div class="flex flex-wrap items-end gap-2 pt-2 border-t border-slate-100">
            <label class="text-xs text-ink-muted">
              Level (0–4)
              <input
                v-model.number="overrideLevels[item.responseEvaluationId]"
                type="number"
                min="0"
                max="4"
                :placeholder="String(item.effectiveLevel ?? 0)"
                class="mt-1 block w-20 rounded-md border border-slate-300 px-2 py-1 text-sm text-ink"
                :data-testid="`override-level-${item.responseEvaluationId}`"
              >
            </label>
            <label class="flex-1 text-xs text-ink-muted">
              Override reason (required for audit log)
              <input
                v-model="overrideReasons[item.responseEvaluationId]"
                type="text"
                placeholder="Explain why this rubric level is overridden"
                class="mt-1 block w-full rounded-md border border-slate-300 px-2 py-1 text-sm text-ink"
                :data-testid="`override-reason-${item.responseEvaluationId}`"
              >
            </label>
            <Button
              v-if="item.reviewStatus === 'flagged' || item.reviewStatus === 'pending'"
              label="Approve As-Is"
              size="small"
              severity="secondary"
              :loading="approveMutation.isPending.value"
              :data-testid="`approve-eval-${item.responseEvaluationId}`"
              @click="approveMutation.mutate(item.responseEvaluationId)"
            />
            <Button
              label="Save Override"
              size="small"
              :loading="overrideMutation.isPending.value"
              :data-testid="`override-submit-${item.responseEvaluationId}`"
              @click="submitOverride(item.responseEvaluationId, item.effectiveLevel)"
            />
          </div>
        </li>
      </ul>

      <!-- REQ-SM-08: Full conversation ledger with timestamps, time to answer, and anomaly/timeout markers -->
      <section class="border-t border-slate-200 pt-4 space-y-3" data-testid="conversation-ledger">
        <div class="flex items-center justify-between">
          <h4 class="text-sm font-semibold text-ink">Full Conversation Ledger</h4>
          <span v-if="transcriptQuery.data.value" class="font-mono text-xs text-ink-muted">
            {{ transcriptQuery.data.value.format }}
          </span>
        </div>

        <div v-if="transcriptQuery.isPending.value" class="text-xs text-ink-muted">
          Loading conversation ledger...
        </div>

        <Message v-else-if="transcriptQuery.isError.value" severity="error" :closable="false">
          Could not load conversation ledger.
        </Message>

        <div
          v-else-if="!transcriptQuery.data.value || transcriptQuery.data.value.entries.length === 0"
          class="text-xs text-ink-muted"
        >
          No conversation entries recorded for this attempt.
        </div>

        <ol v-else class="space-y-3">
          <li
            v-for="entry in transcriptQuery.data.value.entries"
            :key="entry.entryId"
            class="rounded-lg border border-slate-200 bg-surface-sunken/50 p-3 text-xs space-y-2"
            :data-testid="`ledger-entry-${entry.entryId}`"
          >
            <div class="flex flex-wrap items-center justify-between gap-2 text-ink-muted">
              <div class="flex flex-wrap items-center gap-2">
                <span class="font-semibold text-ink">Turn #{{ entry.turn }}</span>
                <Tag :value="entry.question.origin" severity="secondary" />
                <span class="tabular-nums">Time to answer: {{ entry.answer.durationSec }}s</span>
              </div>
              <div class="flex flex-wrap items-center gap-1">
                <Tag
                  v-if="entry.answer.timedOut"
                  value="Timed Out"
                  severity="warn"
                  :data-testid="`marker-timed-out-${entry.entryId}`"
                />
                <Tag
                  v-if="entry.answer.pasteSuspected"
                  value="Paste Suspected"
                  severity="danger"
                  :data-testid="`marker-paste-${entry.entryId}`"
                />
                <Tag
                  v-if="entry.answer.injectionSuspected"
                  value="Injection Suspected"
                  severity="danger"
                  :data-testid="`marker-injection-${entry.entryId}`"
                />
              </div>
            </div>

            <div class="flex flex-wrap gap-x-4 gap-y-1 font-mono text-[11px] text-ink-muted">
              <span>Asked: {{ entry.question.askedAt }}</span>
              <span v-if="entry.answer.submittedAt">Submitted: {{ entry.answer.submittedAt }}</span>
            </div>

            <p class="font-medium text-ink">Q: {{ entry.question.text }}</p>
            <p class="whitespace-pre-wrap text-ink-muted">
              A: {{ entry.answer.text || '(No answer submitted)' }}
            </p>
          </li>
        </ol>
      </section>
    </template>
  </aside>
</template>
