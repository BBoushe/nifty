package de.fhdortmund.nifty.assessment.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.assessment.service.AssessmentSessionService;
import de.fhdortmund.nifty.assessment.service.AttemptStatus;
import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.assessment.service.SessionReply;
import de.fhdortmund.nifty.assessment.service.SessionStateResponse;
import de.fhdortmund.nifty.shared.config.SecurityConfig;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.shared.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** REQ-AC-05, 06, 11, 14, 20 / REQ-AA-01 / Decisions 1.15, 2.26: CANDIDATE-only session endpoints. */
@WebMvcTest(AssessmentSessionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AssessmentSessionControllerTest {

    private static final String BASE = "/api/v1/assessment/sessions";
    private static final UUID ATTEMPT = UUID.randomUUID();
    private static final Instant EXPIRES = Instant.parse("2026-11-26T10:03:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AssessmentSessionService sessions;

    @Test
    void startStreamsInterviewerDeltasAndTheClosingQuestionEvent() throws Exception {
        UUID entryId = UUID.randomUUID();
        when(sessions.start()).thenReturn(new SessionReply.Stream(sink -> {
            sink.send("delta", new SessionReply.Delta("Hello.\nQuestion 1?"));
            sink.send("question", new SessionReply.Question(ATTEMPT, entryId, EntryOrigin.CORE, EXPIRES, 1));
        }));

        dispatch(candidate(post(BASE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(content().string(containsString("event: delta\ndata: {\"text\":\"Hello.\\nQuestion 1?\"}\n\n")))
                .andExpect(content().string(containsString("event: question\ndata: {\"attemptId\":\"" + ATTEMPT
                        + "\",\"entryId\":\"" + entryId
                        + "\",\"origin\":\"core\",\"expiresAt\":\"2026-11-26T10:03:00Z\",\"questionsRemaining\":1}\n\n")));
    }

    @Test
    void softSkillsOnlyStartAnswersJson() throws Exception {
        when(sessions.start()).thenReturn(new SessionReply.State(state(AttemptStatus.BFI10)));

        dispatch(candidate(post(BASE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.attemptId").value(ATTEMPT.toString()))
                .andExpect(jsonPath("$.status").value("BFI10"))
                .andExpect(jsonPath("$.bfi10Enabled").value(true));
    }

    @Test
    void attemptLimitIs409ProblemDetail() throws Exception {
        when(sessions.start()).thenThrow(new DomainException(HttpStatus.CONFLICT, "You have used all attempts."));

        mvc.perform(candidate(post(BASE)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")));
    }

    @Test
    void answerOf4001CharactersIs400OnContentAndNeverReachesTheService() throws Exception {
        mvc.perform(candidate(post(BASE + "/" + ATTEMPT + "/answers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "a".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("content"));

        verify(sessions, never()).answer(any(), anyString());
    }

    @Test
    void answerOfExactly4000CharactersStreamsTheReply() throws Exception {
        String content = "a".repeat(4000);
        when(sessions.answer(ATTEMPT, content)).thenReturn(new SessionReply.Stream(
                sink -> sink.send("done", new SessionReply.Done(AttemptStatus.COMPLETED))));

        dispatch(candidate(post(BASE + "/" + ATTEMPT + "/answers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("event: done\ndata: {\"status\":\"COMPLETED\"}\n\n"));
    }

    @Test
    void answerWithoutContentIs400() throws Exception {
        mvc.perform(candidate(post(BASE + "/" + ATTEMPT + "/answers")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    @Test
    void getReturnsTheSessionStateForARefresh() throws Exception {
        when(sessions.state(ATTEMPT)).thenReturn(state(AttemptStatus.IN_PROGRESS));

        mvc.perform(get(BASE + "/" + ATTEMPT).with(user("cand").roles("CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-26T10:03:00Z"))
                .andExpect(jsonPath("$.messages[0].author").value("interviewer"));
    }

    @Test
    void readingAnotherCandidatesSessionIs403() throws Exception {
        when(sessions.state(ATTEMPT)).thenThrow(new DomainException(HttpStatus.FORBIDDEN, "Not yours."));

        mvc.perform(get(BASE + "/" + ATTEMPT).with(user("cand").roles("CANDIDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void bfi10SubmissionIs204() throws Exception {
        mvc.perform(candidate(post(BASE + "/" + ATTEMPT + "/bfi10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[1,2,3,4,5,5,4,3,2,1]}"))
                .andExpect(status().isNoContent());

        verify(sessions).submitBfi10(ATTEMPT, List.of(1, 2, 3, 4, 5, 5, 4, 3, 2, 1));
    }

    @Test
    void bfi10MissingItemIs400NamingItAndWrongStateIs409() throws Exception {
        doThrow(new FieldValidationException(List.of(new FieldErrorDetail("items[6]", "Item 7 needs an answer.", "BFI10_ITEM"))))
                .when(sessions).submitBfi10(eq(ATTEMPT), any());
        mvc.perform(candidate(post(BASE + "/" + ATTEMPT + "/bfi10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[3,3,3,3,3,3,null,3,3,3]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[6]"));

        UUID other = UUID.randomUUID();
        doThrow(new DomainException(HttpStatus.CONFLICT, "Not at BFI10.")).when(sessions).submitBfi10(eq(other), any());
        mvc.perform(candidate(post(BASE + "/" + other + "/bfi10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[3,3,3,3,3,3,3,3,3,3]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void professorIs403AndAnonymousIs401() throws Exception {
        mvc.perform(post(BASE).with(user("prof").roles("PROFESSOR")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + ATTEMPT)).andExpect(status().isUnauthorized());
        verify(sessions, never()).start();
    }

    private org.springframework.test.web.servlet.ResultActions dispatch(MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult started = mvc.perform(request).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started));
    }

    private static MockHttpServletRequestBuilder candidate(MockHttpServletRequestBuilder request) {
        return request.with(user("cand").roles("CANDIDATE")).with(csrf());
    }

    private static SessionStateResponse state(AttemptStatus status) {
        return new SessionStateResponse(ATTEMPT, status, 1, true, 1, EXPIRES,
                List.of(new SessionStateResponse.Message(1, "interviewer", "Question 1", EXPIRES.minusSeconds(180))));
    }
}
