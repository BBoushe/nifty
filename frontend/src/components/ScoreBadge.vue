<script setup lang="ts">
import { computed } from 'vue'

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

const hasLevel = computed(() => Number.isInteger(props.level) && props.level! >= 0 && props.level! <= 4)
const text = computed(() =>
  hasLevel.value
    ? `${props.level}/4${props.label ? ` · ${props.label}` : ''}`
    : 'N/A · not enough evidence',
)
const classes = computed(() =>
  hasLevel.value ? BAND_CLASSES[props.level!] : 'score-hatch bg-surface-sunken text-ink-muted',
)
</script>

<template>
  <span
    class="inline-flex items-center rounded-pill px-2.5 py-0.5 text-xs font-semibold tabular-nums"
    :class="classes"
  >
    {{ text }}
  </span>
</template>
