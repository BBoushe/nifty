package de.fhdortmund.nifty.shared.exception;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** REQ-BA-05 and REQ-BA-06. Security filters are off: this test is about error mapping only. */
// The 500 case logs its expected stack trace at ERROR; silence only this logger here.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorTestController.class,
        properties = "logging.level.de.fhdortmund.nifty.shared.exception.GlobalExceptionHandler=OFF")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.ErrorTestController.class) // nested classes are excluded from scanning
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidBodyReturnsProblemDetailNamingTheField() throws Exception {
        mockMvc.perform(post("/test/errors/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/test/errors/items"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void domainExceptionReturnsItsStatusAndDetail() throws Exception {
        mockMvc.perform(get("/test/errors/domain"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Config is already approved"))
                .andExpect(jsonPath("$.instance").value("/test/errors/domain"));
    }

    @Test
    void unexpectedExceptionReturnsGenericServerError() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("secret internal state"))))
                .andExpect(content().string(not(containsString("trace"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void fieldValidationExceptionReturnsAllFieldErrorsWithoutInternals() throws Exception {
        mockMvc.perform(get("/test/errors/fields"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("minTimeSec"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at least 30"))
                .andExpect(jsonPath("$.errors[0].code").value("MIN_TIME_TOO_LOW"))
                .andExpect(jsonPath("$.errors[1].field").value("followUpLimit"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("FieldValidationException"))));
    }

    @Test
    void rejectedTaskReturns503ProblemDetailWithoutInternals() throws Exception {
        mockMvc.perform(get("/test/errors/rejected"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The server is busy. Try again in a moment."))
                .andExpect(content().string(not(containsString("TaskRejectedException"))));
    }

    @Test
    void securityExceptionsAreLeftToSpringSecurity() {
        assertThatThrownBy(() -> mockMvc.perform(get("/test/errors/denied")))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    record ItemRequest(@NotBlank String name) {
    }

    @RestController
    static class ErrorTestController {

        @PostMapping("/test/errors/items")
        String create(@Valid @RequestBody ItemRequest request) {
            return request.name();
        }

        @GetMapping("/test/errors/domain")
        String domain() {
            throw new DomainException(HttpStatus.CONFLICT, "Config is already approved");
        }

        @GetMapping("/test/errors/unexpected")
        String unexpected() {
            throw new IllegalStateException("secret internal state");
        }

        @GetMapping("/test/errors/fields")
        String fields() {
            throw new FieldValidationException(List.of(
                    new FieldErrorDetail("minTimeSec", "must be at least 30", "MIN_TIME_TOO_LOW"),
                    new FieldErrorDetail("followUpLimit", "must be at most 3", "FOLLOW_UP_LIMIT_TOO_HIGH")));
        }

        @GetMapping("/test/errors/rejected")
        String rejected() {
            throw new TaskRejectedException("pool is full");
        }

        @GetMapping("/test/errors/denied")
        String denied() {
            throw new AccessDeniedException("denied");
        }
    }
}
