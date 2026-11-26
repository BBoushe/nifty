<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { deleteAssessment, formatPercent, getAssessment } from '@/api/studio'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const configId = computed(() => String(route.params.configId ?? ''))
const confirmDelete = ref(false)

const { data, isPending, isError, error } = useQuery({
  queryKey: computed(() => ['studio', 'assessment', configId.value]),
  queryFn: () => getAssessment(configId.value),
  enabled: computed(() => configId.value.length > 0),
})

const deleteMutation = useMutation({
  mutationFn: () => deleteAssessment(configId.value),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['studio', 'assessments'] })
    await router.push('/ranking/studio')
  },
})
</script>

<template>
  <section class="space-y-6">
    <div v-if="isPending" class="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
      Loading assessment configuration…
    </div>

    <Message v-else-if="isError" severity="error">
      {{ error instanceof Error ? error.message : 'Could not load this assessment.' }}
    </Message>

    <div v-else-if="!data" class="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
      Assessment configuration not found.
    </div>

    <template v-else>
      <div class="flex flex-wrap items-start justify-between gap-4 rounded-xl border border-slate-200 bg-white p-6">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold text-slate-900">{{ data.title }}</h1>
            <Tag :value="`${data.status} v${data.version}`" severity="secondary" />
          </div>
          <p class="mt-2 max-w-2xl text-sm text-slate-600">{{ data.vision }}</p>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <RouterLink
            :to="`/ranking/studio/${data.configId}/invites`"
            class="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            Send to students
          </RouterLink>
          <Button
            v-if="!confirmDelete"
            label="Delete assessment"
            severity="danger"
            outlined
            size="small"
            data-testid="delete-assessment-trigger"
            @click="confirmDelete = true"
          />
          <Button
            v-else
            label="Confirm GDPR purge"
            severity="danger"
            size="small"
            :loading="deleteMutation.isPending.value"
            data-testid="delete-assessment-confirm"
            @click="deleteMutation.mutate()"
          />
        </div>
      </div>

      <Message v-if="deleteMutation.isError.value" severity="error">
        {{ deleteMutation.error.value instanceof Error ? deleteMutation.error.value.message : 'Delete failed.' }}
      </Message>

      <dl class="grid gap-4 rounded-xl border border-slate-200 bg-white p-6 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt class="text-slate-500">Minimum coverage</dt>
          <dd class="font-semibold text-slate-900">{{ formatPercent(data.minCoverage) }}</dd>
        </div>
        <div>
          <dt class="text-slate-500">Max attempts</dt>
          <dd class="font-semibold text-slate-900">{{ data.maxAttempts }}</dd>
        </div>
        <div>
          <dt class="text-slate-500">Team size / Mode</dt>
          <dd class="font-semibold text-slate-900">{{ data.teamSize }} · {{ data.groupingMode }}</dd>
        </div>
        <div>
          <dt class="text-slate-500">BFI-10 / Invented questions</dt>
          <dd class="font-semibold text-slate-900">
            {{ data.bfi10Enabled ? 'On' : 'Off' }} / {{ data.inventedQuestions ? 'On' : 'Off' }}
          </dd>
        </div>
      </dl>

      <div class="space-y-3 rounded-xl border border-slate-200 bg-white p-6">
        <h2 class="text-lg font-semibold text-slate-900">Skills &amp; Hidden Rubrics (0–4)</h2>
        <p v-if="data.skills.length === 0" class="text-sm text-slate-600">
          No hard skills configured (SOFT_SKILLS-only mode).
        </p>
        <div
          v-for="skill in data.skills"
          :key="skill.skillId"
          class="space-y-2 rounded-lg border border-slate-200 p-4"
        >
          <h3 class="font-semibold text-slate-900">{{ skill.name }}</h3>
          <ol class="grid gap-2 text-xs md:grid-cols-5">
            <li
              v-for="(desc, level) in skill.rubricLevels"
              :key="`${skill.skillId}-${level}`"
              class="rounded bg-slate-50 p-2 text-slate-700"
            >
              <span class="font-semibold text-slate-900">L{{ level }}:</span> {{ desc }}
            </li>
          </ol>
        </div>
      </div>

      <div class="space-y-3 rounded-xl border border-slate-200 bg-white p-6">
        <h2 class="text-lg font-semibold text-slate-900">Core Questions</h2>
        <p v-if="data.questions.length === 0" class="text-sm text-slate-600">
          No core questions configured.
        </p>
        <div
          v-for="q in data.questions"
          :key="q.coreQuestionId"
          class="space-y-1 rounded-lg border border-slate-200 p-4 text-sm"
        >
          <p class="font-semibold text-slate-900">{{ q.position }}. {{ q.text }}</p>
          <p class="text-xs text-slate-500">
            Weight {{ q.weight }} · Confidence threshold {{ formatPercent(q.confidenceThreshold) }} ·
            Time {{ q.minTimeSec }}–{{ q.maxTimeSec }}s · Follow-ups ≤ {{ q.followUpLimit }} · Skills:
            {{ q.targetSkillNames.join(', ') }}
          </p>
        </div>
      </div>
    </template>
  </section>
</template>
