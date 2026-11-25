<script setup lang="ts">
import { computed, watchEffect } from 'vue'

// REQ-FF-01: show the backend level as given. A missing level is N/A, never 0.
const props = defineProps<{ level?: number | null; label?: string }>()

// Decision 1.14: fill per band, indexed by level 0..4 (docs/design_system.md, Visual foundations).
const BAND_CLASSES = [
  'bg-danger-soft text-danger',
  'bg-danger-soft text-danger',
  'bg-brand-yellow-soft text-warning-ink',
  'bg-green-soft text-ink',
  'bg-brand-green text-on-green',
]

// null/undefined is N/A. Any other value outside the integers 0..4 is bad backend data, shown as invalid.
const isMissing = computed(() => props.level === null || props.level === undefined)
const hasLevel = computed(() => Number.isInteger(props.level) && props.level! >= 0 && props.level! <= 4)
const text = computed(() => {
  if (isMissing.value) return 'N/A · not enough evidence'
  if (!hasLevel.value) return `Invalid level ${props.level}`
  return `${props.level}/4${props.label ? ` · ${props.label}` : ''}`
})
const classes = computed(() => {
  if (isMissing.value) return 'score-hatch bg-surface-sunken text-ink-muted'
  if (!hasLevel.value) return 'bg-danger-soft text-danger ring-1 ring-danger'
  return BAND_CLASSES[props.level!]
})
watchEffect(() => {
  if (import.meta.env.DEV && !isMissing.value && !hasLevel.value) {
    console.warn(`ScoreBadge: invalid level ${props.level}; expected an integer 0..4 or null`)
  }
})
</script>

<template>
  <span
    class="inline-flex items-center rounded-pill px-2.5 py-0.5 text-xs font-semibold tabular-nums"
    :class="classes"
  >
    {{ text }}
  </span>
</template>
