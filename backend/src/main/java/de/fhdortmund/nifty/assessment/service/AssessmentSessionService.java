package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.assessment.service.AttemptService.Asked;
import de.fhdortmund.nifty.assessment.service.AttemptService.AwaitInterviewer;
import de.fhdortmund.nifty.assessment.service.AttemptService.Finished;
import de.fhdortmund.nifty.assessment.service.AttemptService.ShowState;
import de.fhdortmund.nifty.assessment.service.AttemptService.TurnOutcome;
import de.fhdortmund.nifty.assessment.service.SessionReply.Delta;
import de.fhdortmund.nifty.assessment.service.SessionReply.Done;
import de.fhdortmund.nifty.assessment.service.SessionReply.Question;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.PromptMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * Candidate interview API (REQ-AC-05, REQ-AC-12). Every prompt comes from {@link ContextCreationService}
 * (Decision 1.18). No {@code @Transactional} here on purpose: {@link AttemptService} commits each write, and the
 * interviewer reply streams afterwards, outside any transaction (Decision 1.11).
 */
@Service
public class AssessmentSessionService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentSessionService.class);

    private final AttemptService attempts;
    private final AuthContextService auth;
    private final ContextCreationService context;
    private final ChatClient chat;

    AssessmentSessionService(AttemptService attempts, AuthContextService auth, ContextCreationService context,
            ChatClient.Builder chatBuilder) {
        this.attempts = attempts;
        this.auth = auth;
        this.context = context;
        this.chat = chatBuilder.build();
    }

    public SessionReply start() {
        return toReply(attempts.start(auth.currentCandidate()));
    }

    public SessionReply answer(UUID attemptId, String content) {
        return toReply(attempts.answer(attemptId, auth.currentCandidate().inviteId(), content));
    }

    public SessionStateResponse state(UUID attemptId) {
        return attempts.state(attemptId, auth.currentCandidate().inviteId());
    }

    public void submitBfi10(UUID attemptId, List<Integer> items) {
        attempts.submitBfi10(attemptId, auth.currentCandidate().inviteId(), items);
    }

    private SessionReply toReply(TurnOutcome outcome) {
        return switch (outcome) {
            case ShowState show -> new SessionReply.State(show.state());
            case AwaitInterviewer await -> new SessionReply.Stream(sink -> streamInterviewer(await, sink));
            case Asked asked -> new SessionReply.Stream(sink -> sendOutcome(sink, asked, ""));
            case Finished finished -> new SessionReply.Stream(sink -> sendOutcome(sink, finished, ""));
        };
    }

    private void streamInterviewer(AwaitInterviewer await, SseSink out) {
        TolerantSink sink = new TolerantSink(out);
        BuiltPromptContext prompt = context.buildInterviewerContext(await.input());
        StringBuilder text = new StringBuilder();
        try {
            for (String chunk : chat.prompt().messages(toMessages(prompt)).stream().content().toIterable()) {
                if (chunk != null && !chunk.isEmpty()) {
                    text.append(chunk);
                    sink.send("delta", new Delta(chunk));
                }
            }
        } catch (RuntimeException ex) {
            // The ledger keeps what the candidate saw; with no text, AttemptService asks the configured question.
            log.warn("Interviewer call failed for attempt {}: {}", await.attemptId(), ex.getMessage());
        }
        String streamed = text.toString();
        sendOutcome(sink, attempts.recordInterviewerReply(await.attemptId(), await.step(), streamed), streamed);
    }

    /** Sends the stored question text when it differs from the streamed one (fallback), then the closing event. */
    private static void sendOutcome(SseSink sink, TurnOutcome outcome, String streamed) {
        if (outcome instanceof Asked asked) {
            LedgerEntry q = asked.question();
            if (!q.content().equals(streamed)) {
                sink.send("delta", new Delta(q.content()));
            }
            sink.send("question", new Question(asked.attemptId(), q.id(), q.origin(), q.expiresAt(),
                    asked.questionsRemaining()));
        } else if (outcome instanceof Finished finished) {
            sink.send("done", new Done(finished.status()));
        }
    }

    /** System prompt plus turns as Message objects: candidate text never passes a template renderer. */
    private static List<Message> toMessages(BuiltPromptContext prompt) {
        List<Message> messages = new ArrayList<>(prompt.messages().size() + 1);
        messages.add(new SystemMessage(prompt.systemPrompt()));
        for (int i = 0; i < prompt.messages().size(); i++) {
            PromptMessage m = prompt.messages().get(i);
            messages.add(switch (m.role()) {
                case "user" -> new UserMessage(m.content());
                case "assistant" -> new AssistantMessage(m.content());
                default -> throw new IllegalStateException("Unknown prompt role: " + m.role());
            });
        }
        return messages;
    }

    /** A closed browser tab must not stop the stream: the question still reaches the ledger. */
    private static final class TolerantSink implements SseSink {
        private final SseSink delegate;
        private boolean gone;

        TolerantSink(SseSink delegate) {
            this.delegate = delegate;
        }

        @Override
        public void send(String event, Object data) {
            if (gone) {
                return;
            }
            try {
                delegate.send(event, data);
            } catch (RuntimeException ex) {
                gone = true;
            }
        }
    }
}
