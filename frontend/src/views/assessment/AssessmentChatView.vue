<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  type AttemptStatus,
  type InterviewEvent,
  MAX_ANSWER_CHARS,
  type SessionState,
  getSession,
  sendAnswer,
  startSession,
} from '@/api/assessment'
import { ApiProblem } from '@/api/client'
import Bfi10Form from './Bfi10Form.vue'

// REQ-AC-03: this view shows the conversation, the countdown and the questions left. Nothing else.
interface ChatLine {
  key: string
  author: 'interviewer' | 'candidate'
  content: string
}

const STORAGE_KEY = 'nifty.assessment.attemptId'
const storage = {
  read: (): string | null => {
    try {
      return sessionStorage.getItem(STORAGE_KEY)
    } catch {
      return null
    }
  },
  write: (id: string) => {
    try {
      sessionStorage.setItem(STORAGE_KEY, id)
    } catch {
      // A blocked storage only loses the refresh resume; the backend still resumes on the next start.
    }
  },
}

const attemptId = ref<string | null>(null)
const status = ref<AttemptStatus | null>(null)
const lines = ref<ChatLine[]>([])
const streaming = ref('')
const expiresAt = ref<string | null>(null)
const questionsRemaining = ref(0)
const draft = ref('')
const busy = ref(false)
const failure = ref<string | null>(null)
const now = ref(Date.now())
let localKey = 0

// REQ-AC-06: after a reload, the stored attempt ID resumes the session from the ledger.
const resumeId = ref<string | null>(storage.read())
const {
  data: resumed,
  isLoading: resuming,
  isError: resumeFailed,
} = useQuery({
  queryKey: computed(() => ['assessment', 'session', resumeId.value]),
  queryFn: () => getSession(resumeId.value as string),
  enabled: computed(() => resumeId.value !== null),
})
watch(resumed, (state) => state && applyState(state))

// Decision 2.14: the deadline comes from the server; the browser only counts down to it.
const secondsLeft = computed(() =>
  expiresAt.value === null ? null : Math.max(0, Math.ceil((Date.parse(expiresAt.value) - now.value) / 1000)),
)
const countdown = computed(() => {
  const s = secondsLeft.value ?? 0
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
})
const questionOpen = computed(() => status.value === 'IN_PROGRESS' && expiresAt.value !== null)

let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => (now.value = Date.now()), 1000)
})
onUnmounted(() => clearInterval(timer))

// REQ-AC-10: at zero the typed text goes out as it is. There is no pause.
watch(secondsLeft, (s) => {
  if (s === 0 && questionOpen.value) void submitAnswer()
})

function rememberAttempt(id: string) {
  attemptId.value = id
  storage.write(id)
}

function applyState(state: SessionState) {
  rememberAttempt(state.attemptId)
  status.value = state.status
  lines.value = state.messages.map((m) => ({ key: `seq-${m.seq}`, author: m.author, content: m.content }))
  expiresAt.value = state.expiresAt
  questionsRemaining.value = state.questionsRemaining
}

function addLine(author: ChatLine['author'], content: string) {
  lines.value.push({ key: `local-${localKey++}`, author, content })
}

async function consume(events: AsyncIterable<InterviewEvent>) {
  for await (const event of events) {
    if (event.type === 'delta') {
      streaming.value += event.text
    } else if (event.type === 'question') {
      addLine('interviewer', streaming.value)
      streaming.value = ''
      rememberAttempt(event.attemptId)
      status.value = 'IN_PROGRESS'
      expiresAt.value = event.expiresAt
      questionsRemaining.value = event.questionsRemaining
    } else {
      status.value = event.status
      expiresAt.value = null
    }
  }
}

async function run(action: () => Promise<void>) {
  busy.value = true
  failure.value = null
  try {
    await action()
  } catch (e) {
    // REQ-AC-08: a late answer gets 409; the server already moved on, so reload its state.
    if (e instanceof ApiProblem && e.status === 409 && attemptId.value) {
      applyState(await getSession(attemptId.value))
    }
    failure.value = e instanceof Error ? e.message : 'Something went wrong.'
  } finally {
    busy.value = false
  }
}

function start() {
  return run(async () => {
    const result = await startSession()
    if ('state' in result) applyState(result.state)
    else await consume(result.events)
  })
}

function submitAnswer() {
  if (busy.value || !questionOpen.value || !attemptId.value) return
  const id = attemptId.value
  const content = draft.value
  addLine('candidate', content)
  draft.value = ''
  expiresAt.value = null
  return run(() => consume(sendAnswer(id, content)))
}
</script>

<template>
  <section class="mx-auto max-w-3xl space-y-4">
    <h1 class="text-2xl font-bold text-slate-900">Skill interview</h1>

    <div v-if="resuming" class="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
      Loading your session…
    </div>
    <Message v-else-if="resumeFailed" severity="error">Your session could not be loaded.</Message>

    <div v-if="status === null && !resuming" class="rounded-xl border border-slate-200 bg-white p-6">
      <p class="text-sm text-slate-600">
        Each question has a time limit. The timer does not stop. When it reaches zero, your text is sent as it is.
      </p>
      <Button class="mt-4" data-testid="start" label="Start interview" :loading="busy" @click="start" />
    </div>

    <template v-if="status === 'IN_PROGRESS'">
      <div class="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
        <span data-testid="questions-left">Questions left after this one: {{ questionsRemaining }}</span>
        <span v-if="secondsLeft !== null" class="font-mono text-lg font-semibold" data-testid="countdown">{{ countdown }}</span>
      </div>
    </template>

    <ol v-if="lines.length || streaming" class="space-y-3">
      <li
        v-for="line in lines"
        :key="line.key"
        class="whitespace-pre-wrap rounded-xl px-4 py-3 text-sm"
        :class="line.author === 'interviewer' ? 'bg-white text-slate-900' : 'ml-12 bg-slate-900 text-white'"
      >
        {{ line.content }}
      </li>
      <li v-if="streaming" class="whitespace-pre-wrap rounded-xl bg-white px-4 py-3 text-sm text-slate-900">
        {{ streaming }}
      </li>
    </ol>

    <form v-if="questionOpen" class="space-y-2" @submit.prevent="submitAnswer">
      <Textarea
        v-model="draft"
        data-testid="answer"
        class="w-full"
        rows="6"
        :maxlength="MAX_ANSWER_CHARS"
        aria-label="Your answer"
      />
      <div class="flex items-center justify-between">
        <span class="text-xs text-slate-500" data-testid="answer-counter">{{ draft.length }} / {{ MAX_ANSWER_CHARS }}</span>
        <Button type="submit" label="Send answer" :disabled="busy" />
      </div>
    </form>

    <Message v-if="failure" severity="warn">{{ failure }}</Message>

    <Bfi10Form v-if="status === 'BFI10' && attemptId" :attempt-id="attemptId" @submitted="status = 'COMPLETED'" />

    <div v-if="status === 'COMPLETED'" data-testid="completed" class="rounded-xl border border-slate-200 bg-white p-6 text-sm">
      Thank you. Your interview is complete. Your results appear here after the professor releases them.
    </div>
  </section>
</template>
