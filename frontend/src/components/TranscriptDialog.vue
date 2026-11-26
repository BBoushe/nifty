<script setup lang="ts">
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { computed, nextTick, useTemplateRef, watch } from 'vue'
import {
  formatClock,
  formatDuration,
  originLabels,
  splitHighlights,
  type TranscriptTurn,
} from './transcript'

// REQ-SM-10 (professor, with markers) and REQ-CR-07 (candidate, without): one chat-style reader.
const props = defineProps<{
  title: string
  subtitle?: string
  turns: TranscriptTurn[]
  showMarkers: boolean
  focusEntryId?: string
  highlight?: Record<string, string[]>
  loading?: boolean
  error?: string | null
}>()
const visible = defineModel<boolean>('visible', { default: false })

const scroller = useTemplateRef<HTMLElement>('scroller')

const labels = computed(() => originLabels(props.turns))
const summary = computed(() => {
  let answered = 0
  let timedOut = 0
  let flagged = 0
  let totalSec = 0
  for (const t of props.turns) {
    if (t.answer !== null) answered++
    if (t.markers.timedOut) timedOut++
    if (t.markers.pasteSuspected || t.markers.injectionSuspected) flagged++
    totalSec += t.durationSec ?? 0
  }
  return { asked: props.turns.length, answered, timedOut, flagged, total: formatDuration(totalSec) }
})

// "answered in 1 min 12 s · 10:04"; parts that are unknown are left out, never shown as 0.
function answerMeta(t: TranscriptTurn): string {
  const parts = [t.durationSec === null ? 'answered' : `answered in ${formatDuration(t.durationSec)}`]
  const at = formatClock(t.submittedAt)
  if (at) parts.push(at)
  return parts.join(' · ')
}

function scrollToFocus() {
  const target = scroller.value
    ? Array.from(scroller.value.children).find((el) => (el as HTMLElement).dataset.entryId === props.focusEntryId)
    : undefined
  target?.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
}

// The dialog body only exists while it is open, so @show covers "open with a focus"
// and the watcher covers "focus changes while open".
watch(
  () => props.focusEntryId,
  () => void nextTick(scrollToFocus),
)
</script>

<template>
  <Dialog
    v-model:visible="visible"
    modal
    maximizable
    dismissable-mask
    :style="{ width: 'min(860px, 95vw)' }"
    :breakpoints="{ '640px': '100vw' }"
    data-testid="transcript-dialog"
    @show="scrollToFocus"
  >
    <template #header>
      <div class="min-w-0">
        <h2 class="text-base font-semibold text-ink">{{ title }}</h2>
        <p v-if="subtitle" class="text-xs text-ink-muted">{{ subtitle }}</p>
      </div>
    </template>

    <div v-if="loading" class="py-6 text-sm text-ink-muted">Loading conversation...</div>
    <Message v-else-if="error" severity="error" :closable="false">Could not load the conversation.</Message>
    <div v-else-if="turns.length === 0" class="py-6 text-sm text-ink-muted">
      No conversation entries recorded for this attempt.
    </div>

    <template v-else>
      <dl
        class="mb-4 flex flex-wrap gap-x-6 gap-y-1 rounded-lg bg-surface-sunken px-4 py-2 text-xs text-ink-muted"
        aria-label="Conversation summary"
      >
        <div class="flex gap-1">
          <dt>Questions</dt>
          <dd class="font-semibold tabular-nums text-ink" data-testid="summary-asked">{{ summary.asked }}</dd>
        </div>
        <div class="flex gap-1">
          <dt>Answered</dt>
          <dd class="font-semibold tabular-nums text-ink" data-testid="summary-answered">{{ summary.answered }}</dd>
        </div>
        <div class="flex gap-1">
          <dt>Timed out</dt>
          <dd class="font-semibold tabular-nums text-ink" data-testid="summary-timed-out">{{ summary.timedOut }}</dd>
        </div>
        <div v-if="showMarkers" class="flex gap-1">
          <dt>Flagged</dt>
          <dd
            class="font-semibold tabular-nums"
            :class="summary.flagged > 0 ? 'text-danger' : 'text-ink'"
            data-testid="summary-flagged"
          >
            {{ summary.flagged }}
          </dd>
        </div>
        <div class="flex gap-1">
          <dt>Total time</dt>
          <dd class="font-semibold tabular-nums text-ink" data-testid="summary-total">{{ summary.total }}</dd>
        </div>
      </dl>

      <div
        ref="scroller"
        class="max-h-[60vh] space-y-5 overflow-y-auto px-1 py-1"
        role="region"
        tabindex="0"
        aria-label="Conversation transcript"
        data-testid="transcript-turns"
      >
        <article
          v-for="(t, i) in turns"
          :key="t.id"
          :data-entry-id="t.id"
          :data-testid="`turn-${t.id}`"
          :aria-label="`Turn ${t.turn}, ${labels[i]}`"
          class="space-y-2 rounded-lg"
          :class="t.id === focusEntryId ? 'ring-2 ring-brand-green ring-offset-4' : ''"
        >
          <div class="flex justify-start">
            <div class="max-w-[65ch] rounded-2xl rounded-tl-sm bg-surface-sunken px-4 py-3">
              <p class="mb-1 flex items-center gap-2 text-xs text-ink-muted">
                <span class="font-semibold">{{ labels[i] }}</span>
                <time :datetime="t.askedAt" class="tabular-nums">{{ formatClock(t.askedAt) }}</time>
              </p>
              <p class="text-sm text-ink">{{ t.question }}</p>
            </div>
          </div>

          <div class="flex flex-col items-end gap-1">
            <div
              v-if="t.answer !== null"
              class="max-w-[65ch] rounded-2xl rounded-tr-sm bg-green-soft px-4 py-3"
            >
              <p class="whitespace-pre-wrap text-sm text-ink" :data-testid="`answer-${t.id}`">
                <component
                  :is="seg.mark ? 'mark' : 'span'"
                  v-for="(seg, s) in splitHighlights(t.answer, highlight?.[t.id] ?? [])"
                  :key="s"
                  :class="seg.mark ? 'rounded-sm bg-brand-yellow-soft px-0.5 text-ink' : ''"
                >
                  {{ seg.text }}
                </component>
              </p>
              <p class="mt-1 text-xs text-ink-muted" :data-testid="`answer-meta-${t.id}`">{{ answerMeta(t) }}</p>
            </div>
            <div
              v-else
              class="max-w-[65ch] rounded-2xl rounded-tr-sm border border-dashed border-slate-300 px-4 py-3"
            >
              <p class="text-sm italic text-ink-muted" :data-testid="`no-answer-${t.id}`">No answer</p>
            </div>

            <div v-if="showMarkers" class="flex flex-wrap justify-end gap-1">
              <Tag
                v-if="t.markers.timedOut"
                value="Timed out"
                severity="warn"
                :data-testid="`marker-timed-out-${t.id}`"
              />
              <Tag
                v-if="t.markers.pasteSuspected"
                value="Paste suspected"
                severity="danger"
                :data-testid="`marker-paste-${t.id}`"
              />
              <Tag
                v-if="t.markers.injectionSuspected"
                value="Injection suspected"
                severity="danger"
                :data-testid="`marker-injection-${t.id}`"
              />
            </div>
          </div>
        </article>
      </div>
    </template>

    <template #footer>
      <slot name="actions" />
      <Button label="Close" size="small" severity="secondary" text @click="visible = false" />
    </template>
  </Dialog>
</template>
