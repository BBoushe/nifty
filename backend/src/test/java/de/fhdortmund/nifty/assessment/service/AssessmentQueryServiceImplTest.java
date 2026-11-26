package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.lang.reflect.RecordComponent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** REQ-AC-04 / REQ-AC-15 / REQ-AC-16 / REQ-AC-17: read contracts for evaluation and teams. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AssessmentQueryServiceImpl.class)
class AssessmentQueryServiceImplTest extends PostgresContainerBase {

    private static final Instant T0 = Instant.parse("2026-11-26T10:00:00Z");

    @Autowired
    private AssessmentQueryService query;
    @Autowired
    private JdbcTemplate jdbc;

    private final UUID configId = UUID.randomUUID();

    @Test
    void transcriptPairsEachQuestionWithItsAnswerInOrderAndSkipsAnUnansweredQuestion() {
        UUID invite = UUID.randomUUID();
        UUID attempt = attempt(invite, 1, "COMPLETED");
        UUID core = UUID.randomUUID();
        UUID skill = UUID.randomUUID();
        entry(attempt, 1, "interviewer", core, "core", "What is ACID?", skill, T0, T0.plusSeconds(180), false, false);
        UUID a1 = entry(attempt, 2, "candidate", core, "core", "Atomicity first.", skill, T0.plusSeconds(40), null,
                false, true);
        entry(attempt, 3, "interviewer", core, "follow_up", "Why?", skill, T0.plusSeconds(45), T0.plusSeconds(225),
                false, false);
        UUID a2 = entry(attempt, 4, "candidate", core, "follow_up", "", skill, T0.plusSeconds(260), null, true, false);
        entry(attempt, 5, "interviewer", core, "core", "Open question", skill, T0.plusSeconds(261), null, false, false);

        ImmutableTranscriptView transcript = query.getTranscriptForAttempt(attempt);

        assertThat(transcript.attemptId()).isEqualTo(attempt);
        assertThat(transcript.configId()).isEqualTo(configId);
        assertThat(transcript.inviteId()).isEqualTo(invite);
        assertThat(transcript.configVersion()).isEqualTo("v1");
        assertThat(transcript.attemptNo()).isEqualTo(1);
        assertThat(transcript.entries()).hasSize(2);
        TranscriptEntryView first = transcript.entries().get(0);
        assertThat(first.entryId()).isEqualTo(a1);
        assertThat(first.origin()).isEqualTo(EntryOrigin.CORE);
        assertThat(first.questionText()).isEqualTo("What is ACID?");
        assertThat(first.answerText()).isEqualTo("Atomicity first.");
        assertThat(first.targetSkillIds()).containsExactly(skill);
        assertThat(first.askedAt()).isEqualTo(T0);
        assertThat(first.expiresAt()).isEqualTo(T0.plusSeconds(180));
        assertThat(first.submittedAt()).isEqualTo(T0.plusSeconds(40));
        assertThat(first.durationSec()).isEqualTo(40);
        assertThat(first.pasteSuspected()).isTrue();
        TranscriptEntryView second = transcript.entries().get(1);
        assertThat(second.entryId()).isEqualTo(a2);
        assertThat(second.origin()).isEqualTo(EntryOrigin.FOLLOW_UP);
        assertThat(second.answerText()).isEmpty();
        assertThat(second.timedOut()).isTrue();
    }

    @Test
    void transcriptTypesCarryNoBfi10Field() {
        for (Class<?> type : List.of(ImmutableTranscriptView.class, TranscriptEntryView.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertThat(component.getName().toLowerCase())
                        .doesNotContain("bfi", "trait", "openness", "neuroticism", "extraversion");
            }
        }
    }

    @Test
    void unknownAttemptTranscriptIs404() {
        assertThatThrownBy(() -> query.getTranscriptForAttempt(UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void latestCompletedAttemptIgnoresOlderAndUnfinishedAttempts() {
        UUID invite = UUID.randomUUID();
        attempt(invite, 1, "COMPLETED");
        UUID second = attempt(invite, 2, "COMPLETED");
        attempt(invite, 3, "IN_PROGRESS");

        assertThat(query.getLatestCompletedAttemptId(configId, invite)).contains(second);
        assertThat(query.getLatestCompletedAttemptId(configId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void softSkillProfilesUseEachInvitesLatestCompletedAttemptWithAlreadyScoredMeans() {
        UUID inviteA = UUID.randomUUID();
        bfi(attempt(inviteA, 1, "COMPLETED"), "1.00");
        UUID latestA = attempt(inviteA, 2, "COMPLETED");
        bfi(latestA, "4.50");
        UUID inviteB = UUID.randomUUID();
        UUID onlyB = attempt(inviteB, 1, "COMPLETED");
        attempt(UUID.randomUUID(), 1, "BFI10");

        List<StudentSoftSkillProfile> profiles = query.getSoftSkillProfilesForConfig(configId);

        assertThat(profiles).extracting(StudentSoftSkillProfile::attemptId).containsExactlyInAnyOrder(latestA, onlyB);
        StudentSoftSkillProfile a = profiles.stream().filter(p -> p.inviteId().equals(inviteA)).findFirst().orElseThrow();
        assertThat(a.softSkillScores()).isEqualTo(Map.of("conscientiousness", 4.5, "agreeableness", 4.5,
                "neuroticism", 4.5, "extraversion", 4.5, "openness", 4.5));
        StudentSoftSkillProfile b = profiles.stream().filter(p -> p.inviteId().equals(inviteB)).findFirst().orElseThrow();
        assertThat(b.softSkillScores()).isNull();
    }

    @Test
    void softSkillProfileForAttemptNeedsACompletedAttempt() {
        UUID done = attempt(UUID.randomUUID(), 1, "COMPLETED");
        bfi(done, "3.00");

        assertThat(query.getSoftSkillProfileForAttempt(done)).hasValueSatisfying(
                p -> assertThat(p.softSkillScores()).containsEntry("openness", 3.0));
        assertThat(query.getSoftSkillProfileForAttempt(attempt(UUID.randomUUID(), 1, "BFI10"))).isEmpty();
        assertThat(query.getSoftSkillProfileForAttempt(UUID.randomUUID())).isEmpty();
    }

    private UUID attempt(UUID invite, int attemptNo, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assessment_attempt (id, invite_id, config_id, attempt_no, status, started_at, "
                + "completed_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id, invite, configId, attemptNo, status,
                Timestamp.from(T0), "COMPLETED".equals(status) ? Timestamp.from(T0.plusSeconds(600)) : null);
        return id;
    }

    private UUID entry(UUID attempt, int seq, String author, UUID core, String origin, String content, UUID skill,
            Instant createdAt, Instant expiresAt, boolean timedOut, boolean paste) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO conversation_entry (id, attempt_id, seq, author, core_question_id, origin, content, "
                + "target_skills, created_at, expires_at, timed_out, paste_suspected) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)", id, attempt, seq, author, core, origin, content,
                "[\"" + skill + "\"]", Timestamp.from(createdAt), expiresAt == null ? null : Timestamp.from(expiresAt),
                timedOut, paste);
        return id;
    }

    private void bfi(UUID attempt, String trait) {
        jdbc.update("INSERT INTO bfi_response (attempt_id, item_1, item_2, item_3, item_4, item_5, item_6, item_7, "
                + "item_8, item_9, item_10, conscientiousness, agreeableness, neuroticism, extraversion, openness, "
                + "submitted_at) VALUES (?, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, ?::numeric, ?::numeric, ?::numeric, "
                + "?::numeric, ?::numeric, now())", attempt, trait, trait, trait, trait, trait);
    }
}
