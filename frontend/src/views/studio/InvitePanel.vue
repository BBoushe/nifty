<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { computed, ref, watch } from 'vue'
import { ApiProblem } from '@/api/client'
import {
  type CreatedInvite,
  createInvites,
  formatInvitesCsv,
  listInvites,
  parseCandidateCsv,
  revokeInvite,
} from '@/api/invites'

const props = defineProps<{ configId: string }>()

const queryClient = useQueryClient()
const singleRef = ref('')
const latestCreated = ref<CreatedInvite[]>([])
const actionError = ref<string | null>(null)
const copiedId = ref<string | null>(null)
const revokingId = ref<string | null>(null)

watch(
  () => props.configId,
  () => {
    singleRef.value = ''
    latestCreated.value = []
    actionError.value = null
    copiedId.value = null
    revokingId.value = null
  },
)

const queryKey = computed(() => ['studio', 'invites', props.configId])

const { data, isPending, isError, error } = useQuery({
  queryKey,
  queryFn: () => listInvites(props.configId),
  enabled: computed(() => props.configId.length > 0),
})

const createMutation = useMutation({
  mutationFn: (refs: string[]) => createInvites(props.configId, refs),
  onSuccess: async (created) => {
    actionError.value = null
    latestCreated.value = created
    singleRef.value = ''
    await queryClient.invalidateQueries({ queryKey: queryKey.value })
  },
  onError: (err) => {
    if (err instanceof ApiProblem) {
      const details = err.errors.map((e) => e.message).join('; ')
      actionError.value = details || err.detail || err.title
    } else {
      actionError.value = 'Could not create invite links.'
    }
  },
})

const revokeMutation = useMutation({
  mutationFn: (inviteId: string) => {
    revokingId.value = inviteId
    return revokeInvite(props.configId, inviteId)
  },
  onSuccess: async () => {
    actionError.value = null
    await queryClient.invalidateQueries({ queryKey: queryKey.value })
  },
  onError: (err) => {
    actionError.value = err instanceof Error ? err.message : 'Could not revoke invite.'
  },
  onSettled: () => {
    revokingId.value = null
  },
})

function onAddSingle() {
  const trimmed = singleRef.value.trim()
  if (!trimmed) {
    actionError.value = 'Enter a candidate reference.'
    return
  }
  actionError.value = null
  createMutation.mutate([trimmed])
}

async function onCsvSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    const refs = parseCandidateCsv(text)
    if (refs.length === 0) {
      actionError.value = 'CSV file contained no candidate references.'
      return
    }
    actionError.value = null
    createMutation.mutate(refs)
  } finally {
    input.value = ''
  }
}

async function copyLink(invite: CreatedInvite) {
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(invite.link)
      copiedId.value = invite.inviteId
      actionError.value = null
    } catch {
      actionError.value = 'Could not copy link to clipboard.'
    }
  }
}

function downloadLinksCsv() {
  const csv = formatInvitesCsv(latestCreated.value)
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `invites-${props.configId}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <section id="invites" class="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
    <div>
      <h2 class="text-lg font-semibold text-slate-900">Student Invite Links</h2>
      <p class="mt-1 text-sm text-slate-600">
        Generate personal invite links one at a time or import a CSV with a <code>candidate_ref</code> column.
        Links are shown once on creation; only hashes are stored.
      </p>
    </div>

    <form class="flex flex-wrap items-end gap-3" @submit.prevent="onAddSingle">
      <div class="flex min-w-60 flex-1 flex-col gap-1">
        <label for="invite-single-input" class="text-xs font-medium text-slate-700">
          Candidate reference
        </label>
        <InputText
          id="invite-single-input"
          v-model="singleRef"
          data-testid="invite-single-input"
          placeholder="e.g., student-042 or email"
          class="w-full"
        />
      </div>
      <Button
        type="submit"
        label="Create invite"
        size="small"
        :loading="createMutation.isPending.value"
        data-testid="invite-single-submit"
      />
      <label
        class="inline-flex cursor-pointer items-center rounded-lg border border-slate-300 px-3 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
      >
        Import CSV
        <input
          type="file"
          accept=".csv,text/csv"
          class="sr-only"
          data-testid="invite-csv-input"
          @change="onCsvSelected"
        >
      </label>
    </form>

    <Message v-if="actionError" severity="error">
      {{ actionError }}
    </Message>

    <div
      v-if="latestCreated.length > 0"
      class="space-y-2 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm"
    >
      <div class="flex items-center justify-between gap-2">
        <p class="font-semibold text-emerald-900">
          Generated links (copy or download now — plain tokens are never shown again):
        </p>
        <Button
          label="Download CSV"
          size="small"
          severity="secondary"
          outlined
          data-testid="invite-download-csv"
          @click="downloadLinksCsv"
        />
      </div>
      <ul class="space-y-1.5">
        <li
          v-for="inv in latestCreated"
          :key="inv.inviteId"
          class="flex flex-wrap items-center justify-between gap-2 rounded bg-white px-3 py-2 text-xs"
        >
          <span class="font-medium text-slate-900">{{ inv.candidateRef }}</span>
          <code class="break-all text-slate-700">{{ inv.link }}</code>
          <Button
            :label="copiedId === inv.inviteId ? 'Copied' : 'Copy'"
            size="small"
            text
            @click="copyLink(inv)"
          />
        </li>
      </ul>
    </div>

    <div v-if="isPending" class="text-sm text-slate-600">Loading invites…</div>

    <Message v-else-if="isError" severity="error">
      {{ error instanceof Error ? error.message : 'Failed to load invites.' }}
    </Message>

    <p v-else-if="!data || data.length === 0" class="text-sm text-slate-600">
      No invites issued yet.
    </p>

    <ul v-else class="divide-y divide-slate-200 rounded-lg border border-slate-200">
      <li
        v-for="inv in data"
        :key="inv.inviteId"
        class="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm"
      >
        <div>
          <p class="font-medium text-slate-900">{{ inv.candidateRef }}</p>
          <p class="text-xs text-slate-500">Created {{ inv.createdAt }}</p>
        </div>
        <div class="flex items-center gap-2">
          <Tag :value="inv.revoked ? 'Revoked' : 'Active'" :severity="inv.revoked ? 'danger' : 'success'" />
          <Button
            v-if="!inv.revoked"
            label="Revoke"
            severity="danger"
            text
            size="small"
            :loading="revokingId === inv.inviteId"
            :data-testid="`revoke-invite-${inv.inviteId}`"
            @click="revokeMutation.mutate(inv.inviteId)"
          />
        </div>
      </li>
    </ul>
  </section>
</template>
