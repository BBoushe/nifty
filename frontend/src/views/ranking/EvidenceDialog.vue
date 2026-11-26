<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
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
import TranscriptDialog from '@/components/TranscriptDialog.vue'
import { fromTranscriptExport } from '@/components/transcript'

// REQ-SM-09: evidence review is a modal; the matrix stays mounted behind the mask.
const props = defineProps<{ attemptId: string | null }>()
const visible = defineModel<boolean>('visible', { default: false })
const emit = defineEmits<{ updated: [] }>()

const queryClient = useQueryClient()
const overrideLevels = reactive<Record<string, number>>({})
const overrideReasons = reactive<Record<string, string>>({})
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const transcriptOpen = ref(false)
const focusEntryId = ref<string | undefined>(undefined)

watch(
  () => props.attemptId,
  () => {
    actionError.value = null
    actionSuccess.value = null
    transcriptOpen.value = false
    focusEntryId.value = undefined
  },
)

const drilldownQuery = useQuery({
  queryKey: computed(() => ['attempt-evaluations', props.attemptId]),
  queryFn: () => getAttemptEvaluations(props.attemptId!),
  enabled: computed(() => Boolean(visible.value && props.attemptId)),
})

// REQ-SM-08: the full conversation with timestamps, durationSec, and anomaly markers.
const transcriptQuery = useQuery({
  queryKey: computed(() => ['attempt-transcript', props.attemptId]),
  queryFn: () => getTranscriptExport(props.attemptId!),
  enabled: computed(() => Boolean(visible.value && props.attemptId)),
})

const turns = computed(() => (transcriptQuery.data.value?.entries ?? []).map(fromTranscriptExport))

// Every verified quote, by entry, so the transcript shows what the evidence rests on.
const highlight = computed(() => {
  const byEntry: Record<string, string[]> = {}
  for (const item of drilldownQuery.data.value?.responses ?? []) {
    if (!item.quoteVerified || !item.verifiedQuote) continue
    const quotes = byEntry[item.entryId] ?? []
    quotes.push(item.verifiedQuote)
    byEntry[item.entryId] = quotes
  }
  return byEntry
})

function openTranscript(entryId?: string) {
  focusEntryId.value = entryId
  transcriptOpen.value = true
}

// shortcut: shows the short invite id until the API returns candidateRef (Tasks 3 and 4 of mvp-hardening).
const candidateName = computed(() => drilldownQuery.data.value?.inviteId.slice(0, 8) ?? 'Evidence review')

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
  <!-- PrimeVue binds Esc on the document for every open Dialog. While the transcript dialog is open on
       top, this one ignores Esc, so one press closes only the top dialog. -->
  <Dialog
    v-model:visible="visible"
    modal
    maximizable
    dismissable-mask
    :close-on-escape="!transcriptOpen"
    :style="{ width: 'min(1100px, 95vw)' }"
    :breakpoints="{ '640px': '100vw' }"
    :pt="{ content: { class: 'space-y-4' } }"
    data-testid="evidence-dialog"
  >
    <template #header>
      <div class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
        <h2 class="text-base font-semibold text-ink">{{ candidateName }}</h2>
        <span v-if="drilldownQuery.data.value" class="text-sm text-ink-muted">
          Attempt {{ drilldownQuery.data.value.attemptNo }}
        </span>
        <Tag v-if="drilldownQuery.data.value" :value="drilldownQuery.data.value.releaseStatus" />
      </div>
    </template>

    <Message v-if="actionError" severity="error" :closable="false">{{ actionError }}</Message>
    <Message v-if="actionSuccess" severity="success" :closable="false">{{ actionSuccess }}</Message>

    <div v-if="drilldownQuery.isPending.value" class="text-sm text-ink-muted">Loading evidence...</div>
    <Message v-else-if="drilldownQuery.isError.value" severity="error" :closable="false">
      Could not load attempt evaluations.
    </Message>

    <template v-else-if="drilldownQuery.data.value">
      <div class="grid gap-3 sm:grid-cols-3 text-xs">
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
          <Button
            v-if="item.entryId"
            label="Show in transcript"
            size="small"
            severity="secondary"
            text
            :data-testid="`show-in-transcript-${item.responseEvaluationId}`"
            @click="openTranscript(item.entryId)"
          />

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
    </template>

    <template #footer>
      <Button label="Close" size="small" severity="secondary" text data-testid="evidence-close-btn" @click="visible = false" />
      <Button label="Export JSON" size="small" severity="secondary" @click="downloadTranscript" />
      <Button
        label="View transcript"
        size="small"
        severity="secondary"
        data-testid="view-transcript-btn"
        @click="openTranscript()"
      />
      <Button
        label="Release Attempt"
        size="small"
        :disabled="!drilldownQuery.data.value || drilldownQuery.data.value.flaggedCount > 0 || isAlreadyReleased"
        :loading="releaseMutation.isPending.value"
        data-testid="release-attempt-btn"
        @click="releaseMutation.mutate()"
      />
    </template>
  </Dialog>

  <!-- REQ-SM-10: the full conversation, with professor markers, over the evidence dialog. -->
  <TranscriptDialog
    v-model:visible="transcriptOpen"
    title="Conversation"
    :subtitle="candidateName"
    :turns="turns"
    :show-markers="true"
    :focus-entry-id="focusEntryId"
    :highlight="highlight"
    :loading="transcriptQuery.isPending.value"
    :error="transcriptQuery.isError.value"
  >
    <template #actions>
      <Button label="Export JSON" size="small" severity="secondary" @click="downloadTranscript" />
    </template>
  </TranscriptDialog>
</template>
