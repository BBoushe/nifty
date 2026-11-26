package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.assessment.service.AttemptService.Asked;
import de.fhdortmund.nifty.assessment.service.AttemptService.AwaitInterviewer;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.Ask;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.CandidateSessionPrincipal;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.view.InterviewerQuestion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** REQ-AC-11: two parallel starts of one invite collide on UNIQUE (invite_id, attempt_no); the loser gets 409. */
class AssessmentSessionServiceTest {

    @Test
    void serverTimestampsMatchTheMicrosecondPrecisionOfPostgres() {
        // Linux clocks tick in nanoseconds (CI failed on 15:06:42.758528053Z vs stored 15:06:42.758528Z).
        for (int i = 0; i < 1000; i++) {
            assertThat(AttemptService.now().getNano() % 1000).isZero();
        }
    }

    @Test
    void parallelStartThatLosesTheAttemptNumberRaceIs409NotA500() {
        AttemptService attempts = mock(AttemptService.class);
        AuthContextService auth = mock(AuthContextService.class);
        when(auth.currentCandidate()).thenReturn(new CandidateSessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), "c", "hash"));
        when(attempts.start(any())).thenThrow(new DataIntegrityViolationException("uq_attempt"));
        AssessmentSessionService sessions = new AssessmentSessionService(attempts, auth,
                mock(ContextCreationService.class), ChatClient.builder(mock(ChatModel.class)), executor(1, 1));

        assertThatThrownBy(sessions::start).isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    /** REQ-AC-23, Decision 2.29: a prompt-build failure falls back to the configured question in the same call. */
    @Test
    void promptBuildFailureSendsTheConfiguredQuestionAtOnce() {
        UUID attemptId = UUID.randomUUID();
        AttemptService attempts = mock(AttemptService.class);
        AuthContextService auth = mock(AuthContextService.class);
        ContextCreationService context = mock(ContextCreationService.class);
        when(auth.currentCandidate()).thenReturn(new CandidateSessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), "c", "hash"));
        InterviewerQuestion q = new InterviewerQuestion(UUID.randomUUID(), 1, "Configured question?", 30, 300, 0,
                List.of(), List.of());
        Ask step = new AttemptStateMachine.AskCore(q);
        when(attempts.answer(any(), any(), any())).thenReturn(new AwaitInterviewer(attemptId, step, null));
        when(context.buildInterviewerContext(any())).thenThrow(new IllegalStateException("boom"));
        Instant now = Instant.now();
        LedgerEntry asked = new LedgerEntry(UUID.randomUUID(), 1, LedgerEntry.INTERVIEWER, q.coreQuestionId(),
                EntryOrigin.CORE, q.text(), List.of(), now, now.plusSeconds(300), false, false, false);
        when(attempts.recordInterviewerReply(attemptId, step, "")).thenReturn(new Asked(attemptId, asked, 0));
        AssessmentSessionService sessions = new AssessmentSessionService(attempts, auth, context,
                ChatClient.builder(mock(ChatModel.class)), executor(1, 1));
        List<String> events = new ArrayList<>();
        List<Object> data = new ArrayList<>();

        SessionReply.Stream stream = (SessionReply.Stream) sessions.answer(attemptId, "my answer");
        stream.body().accept((event, payload) -> {
            events.add(event);
            data.add(payload);
        });

        verify(attempts).recordInterviewerReply(attemptId, step, "");
        assertThat(events).containsExactly("delta", "question");
        assertThat(data.getFirst()).isEqualTo(new SessionReply.Delta("Configured question?"));
    }

    /** REQ-AC-24: with every stream thread busy and no queue room, the answer gets 503 and nothing is stored. */
    @Test
    void fullStreamPoolAnswers503BeforeAnythingIsStored() throws Exception {
        AttemptService attempts = mock(AttemptService.class);
        AuthContextService auth = mock(AuthContextService.class);
        when(auth.currentCandidate()).thenReturn(new CandidateSessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), "c", "hash"));
        ThreadPoolTaskExecutor pool = executor(1, 0);
        CountDownLatch release = new CountDownLatch(1);
        pool.execute(() -> awaitQuietly(release));
        AssessmentSessionService sessions = new AssessmentSessionService(attempts, auth,
                mock(ContextCreationService.class), ChatClient.builder(mock(ChatModel.class)), pool);

        try {
            assertThatThrownBy(() -> sessions.answer(UUID.randomUUID(), "x")).isInstanceOfSatisfying(
                    DomainException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            verifyNoInteractions(attempts);
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    private static ThreadPoolTaskExecutor executor(int maxThreads, int queueCapacity) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(maxThreads);
        pool.setMaxPoolSize(maxThreads);
        pool.setQueueCapacity(queueCapacity);
        pool.initialize();
        return pool;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
