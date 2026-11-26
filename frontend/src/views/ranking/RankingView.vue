<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  applyFormulaPreset,
  formatScore,
  listFormulaPresets,
  previewFormulaPreset,
  type FormulaPreviewResponse,
} from '@/api/evaluation'
import { listAssessments } from '@/api/studio'
import CohortGapsPanel from './CohortGapsPanel.vue'
import EvidenceDialog from './EvidenceDialog.vue'
import SkillsMatrixPanel from './SkillsMatrixPanel.vue'

const route = useRoute()
const queryClient = useQueryClient()

const assessmentsQuery = useQuery({
  queryKey: ['studio-assessments'],
  queryFn: listAssessments,
})

const presetsQuery = useQuery({
  queryKey: ['formula-presets'],
  queryFn: listFormulaPresets,
})

const selectedConfigId = ref<string>(typeof route.params.configId === 'string' ? route.params.configId : '')
const activeTab = ref<'matrix' | 'gaps' | 'formula'>('matrix')
const selectedAttemptId = ref<string | null>(null)
const selectedPreset = ref('WEIGHTED_MEAN')
const previewData = ref<FormulaPreviewResponse | null>(null)
const formulaMessage = ref<string | null>(null)

watch(
  () => assessmentsQuery.data.value,
  (list) => {
    if (!selectedConfigId.value && list && list.length > 0) {
      selectedConfigId.value = list[0].configId
    }
  },
  { immediate: true },
)

watch(selectedConfigId, () => {
  selectedAttemptId.value = null
  previewData.value = null
  formulaMessage.value = null
})

const applyFormulaMutation = useMutation({
  mutationFn: () => applyFormulaPreset(selectedConfigId.value, selectedPreset.value),
  onSuccess: (res) => {
    formulaMessage.value = `Saved formula v${res.versionNumber} (${res.preset}) and recomputed ${res.recomputedCandidates} candidate(s).`
    queryClient.invalidateQueries({ queryKey: ['evaluation-matrix', selectedConfigId.value] })
    queryClient.invalidateQueries({ queryKey: ['cohort-gaps', selectedConfigId.value] })
  },
})

const previewFormulaMutation = useMutation({
  mutationFn: () => previewFormulaPreset(selectedConfigId.value, selectedPreset.value),
  onSuccess: (res) => {
    previewData.value = res
  },
})

const hasSelection = computed(() => Boolean(selectedConfigId.value))
</script>

<template>
  <section class="space-y-6">
    <div class="rounded-xl border border-slate-200 bg-surface p-6 shadow-card">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p class="text-xs font-semibold uppercase tracking-wide text-ink-muted">
            Ranking View — Professor Control Plane
          </p>
          <h1 class="mt-1 text-2xl font-bold text-ink">Evaluation &amp; Skills Matrix</h1>
        </div>
        <label class="text-xs text-ink-muted">
          Assessment
          <select
            v-model="selectedConfigId"
            class="mt-1 block min-w-60 rounded-md border border-slate-300 bg-surface px-3 py-1.5 text-sm text-ink"
            data-testid="ranking-config-select"
          >
            <option value="" disabled>Select an assessment</option>
            <option
              v-for="item in assessmentsQuery.data.value ?? []"
              :key="item.configId"
              :value="item.configId"
            >
              {{ item.title }} (v{{ item.version }})
            </option>
          </select>
        </label>
      </div>

      <div class="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
        <Button
          label="Skills Matrix & Queue"
          size="small"
          :severity="activeTab === 'matrix' ? undefined : 'secondary'"
          @click="activeTab = 'matrix'"
        />
        <Button
          label="Cohort Gaps (SQL)"
          size="small"
          :severity="activeTab === 'gaps' ? undefined : 'secondary'"
          @click="activeTab = 'gaps'"
        />
        <Button
          label="Formula Presets"
          size="small"
          :severity="activeTab === 'formula' ? undefined : 'secondary'"
          @click="activeTab = 'formula'"
        />
      </div>
    </div>

    <div v-if="assessmentsQuery.isPending.value" class="text-sm text-ink-muted">
      Loading assessments...
    </div>

    <Message v-else-if="assessmentsQuery.isError.value" severity="error" :closable="false">
      Failed to load assessments.
    </Message>

    <div v-else-if="!hasSelection" class="rounded-xl border border-slate-200 bg-surface p-6 text-sm text-ink-muted">
      Create an assessment in Studio to review evaluations and cohort gaps here.
      <RouterLink to="/ranking/studio/new" class="font-medium text-slate-900 underline" data-testid="ranking-empty-studio-link">
        Open Studio
      </RouterLink>
    </div>

    <template v-else>
      <SkillsMatrixPanel
        v-if="activeTab === 'matrix'"
        :config-id="selectedConfigId"
        @select-attempt="(id) => (selectedAttemptId = id)"
      />

      <EvidenceDialog
        v-if="activeTab === 'matrix'"
        :attempt-id="selectedAttemptId"
        :visible="selectedAttemptId !== null"
        @update:visible="(open) => !open && (selectedAttemptId = null)"
      />

      <CohortGapsPanel v-if="activeTab === 'gaps'" :config-id="selectedConfigId" />

      <div v-if="activeTab === 'formula'" class="rounded-xl border border-slate-200 bg-surface p-5 space-y-4">
        <h2 class="text-lg font-semibold text-ink">Formula Presets &amp; What-If Preview</h2>
        <Message v-if="formulaMessage" severity="success" :closable="false">{{ formulaMessage }}</Message>
        <div class="flex flex-wrap items-end gap-3">
          <label class="text-xs text-ink-muted">
            Preset
            <select
              v-model="selectedPreset"
              class="mt-1 block rounded-md border border-slate-300 bg-surface px-3 py-1.5 text-sm text-ink"
            >
              <option
                v-for="p in presetsQuery.data.value ?? [{ preset: 'WEIGHTED_MEAN', label: 'Question-Weighted Mean' }]"
                :key="p.preset"
                :value="p.preset"
              >
                {{ p.label }}
              </option>
            </select>
          </label>
          <Button
            label="Preview What-If"
            size="small"
            severity="secondary"
            :loading="previewFormulaMutation.isPending.value"
            @click="previewFormulaMutation.mutate()"
          />
          <Button
            label="Save New Formula Version"
            size="small"
            :loading="applyFormulaMutation.isPending.value"
            @click="applyFormulaMutation.mutate()"
          />
        </div>

        <div v-if="previewData" class="rounded-lg bg-surface-sunken p-4 text-xs space-y-2">
          <p class="font-semibold text-ink">
            Preview ({{ previewData.preset }}) — {{ previewData.candidates.length }} candidate(s) (not saved)
          </p>
          <ul class="space-y-1">
            <li v-for="c in previewData.candidates" :key="c.attemptId" class="font-mono text-ink-muted">
              {{ c.inviteId.slice(0, 8) }}: fit {{ formatScore(c.fitScore) }}
            </li>
          </ul>
        </div>
      </div>
    </template>
  </section>
</template>
