package de.fhdortmund.nifty.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * REQ-IS-05, REQ-IS-06, REQ-IS-11, Decision 2.23: pure-JVM unit tests covering every validation error code
 * and the SOFT_SKILLS + bfi10Enabled zero-question exemption.
 */
class ConfigValidatorTest {

    private static final List<String> RUBRIC_5 = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    private final ConfigValidator validator = new ConfigValidator();

    @Test
    void acceptsValidHardSkillsConfig() {
        assertThatCode(() -> validator.validateOrThrow(validRequest())).doesNotThrowAnyException();
    }

    @Test
    void acceptsSoftSkillsOnlyWithZeroSkillsAndZeroQuestionsWhenBfi10Enabled() {
        SettingsInput settings = new SettingsInput(
                "Personality Team Match",
                "Form balanced teams using BFI-10",
                new BigDecimal("0.0000"),
                1,
                true,
                4,
                GroupingMode.SOFT_SKILLS,
                SoftPreset.ENGINEERING_DEFAULT,
                new BigDecimal("0.0000"),
                new BigDecimal("0.6000"),
                Map.of());

        assertThatCode(() -> validator.validateOrThrow(new CreateAssessmentRequest(settings, List.of(), List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptySkillsAndQuestionsForHardSkillsAndCombinedAndSoftWithoutBfi10() {
        CreateAssessmentRequest hardEmpty = new CreateAssessmentRequest(validSettings(GroupingMode.HARD_SKILLS, false),
                List.of(), List.of());
        List<FieldErrorDetail> errors = validateErrors(hardEmpty);
        assertThat(errors).extracting(FieldErrorDetail::code).contains("EMPTY_CONFIG");
        assertThat(errors).extracting(FieldErrorDetail::field).contains("skills", "questions");

        CreateAssessmentRequest softNoBfi = new CreateAssessmentRequest(validSettings(GroupingMode.SOFT_SKILLS, false),
                List.of(), List.of());
        List<FieldErrorDetail> softErrors = validateErrors(softNoBfi);
        assertThat(softErrors).extracting(FieldErrorDetail::code).contains("EMPTY_CONFIG", "BFI10_REQUIRED");
        assertThat(softErrors).extracting(FieldErrorDetail::field).contains("settings.bfi10Enabled");
    }

    @Test
    void collectsMultipleErrorsAtOnceIncludingTimeRangeFollowUpAndRubric() {
        SettingsInput settings = validSettings(GroupingMode.HARD_SKILLS, false);
        SkillInput badRubricSkill = new SkillInput("Java", List.of("None", " ", "Developing", "Proficient", "Expert"));
        QuestionInput badQuestion = new QuestionInput(
                1,
                "Explain ACID",
                new BigDecimal("1.00"),
                new BigDecimal("0.7000"),
                120,
                60,
                4,
                List.of("Java"));

        List<FieldErrorDetail> errors = validateErrors(
                new CreateAssessmentRequest(settings, List.of(badRubricSkill), List.of(badQuestion)));

        assertThat(errors).hasSize(3);
        assertThat(errors).extracting(FieldErrorDetail::code)
                .containsExactlyInAnyOrder("RUBRIC_INCOMPLETE", "TIME_RANGE", "OUT_OF_RANGE");
        assertThat(errors).extracting(FieldErrorDetail::field)
                .contains("questions[0].maxTimeSec", "questions[0].followUpLimit", "skills[0].rubricLevels");
    }

    @Test
    void detectsRequiredOutOfRangeAndBadWeightsOnSettings() {
        SettingsInput settings = new SettingsInput(
                "   ",
                "",
                new BigDecimal("1.2000"),
                0,
                true,
                1,
                GroupingMode.COMBINED,
                SoftPreset.CUSTOM,
                new BigDecimal("-0.1000"),
                new BigDecimal("1.5000"),
                Map.of("conscientiousness", new BigDecimal("0.50")));

        List<FieldErrorDetail> errors = validateErrors(new CreateAssessmentRequest(
                settings,
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(validQuestion(1, List.of("Java")))));

        assertThat(errors).extracting(FieldErrorDetail::code)
                .contains("REQUIRED", "OUT_OF_RANGE", "BAD_WEIGHTS");
        assertThat(errors).extracting(FieldErrorDetail::field)
                .contains("settings.title", "settings.vision", "settings.minCoverage",
                        "settings.maxAttempts", "settings.teamSize", "settings.combinedHardWeight",
                        "settings.alpha", "settings.constraintWeights");
    }

    @Test
    void acceptsValidCustomSoftPresetWeightsSummingToOne() {
        SettingsInput settings = new SettingsInput(
                "Combined Assessment",
                "Vision",
                new BigDecimal("0.5000"),
                1,
                true,
                4,
                GroupingMode.COMBINED,
                SoftPreset.CUSTOM,
                new BigDecimal("0.6000"),
                new BigDecimal("0.5000"),
                Map.of(
                        "conscientiousness", new BigDecimal("0.30"),
                        "agreeableness", new BigDecimal("0.20"),
                        "neuroticism", new BigDecimal("0.10"),
                        "extraversion", new BigDecimal("0.20"),
                        "openness", new BigDecimal("0.20")));

        assertThatCode(() -> validator.validateOrThrow(new CreateAssessmentRequest(
                settings,
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(validQuestion(1, List.of("Java")))))).doesNotThrowAnyException();
    }

    @Test
    void detectsDuplicateSkillUnusedSkillNoSkillAndOrderGap() {
        List<SkillInput> skills = List.of(
                new SkillInput("Java", RUBRIC_5),
                new SkillInput(" java ", RUBRIC_5),
                new SkillInput("Docker", RUBRIC_5));
        List<QuestionInput> questions = List.of(
                new QuestionInput(1, "Q1", new BigDecimal("1.00"), new BigDecimal("0.7000"), 30, 120, 1,
                        List.of("Java")),
                new QuestionInput(3, "Q2", new BigDecimal("1.00"), new BigDecimal("0.7000"), 30, 120, 1,
                        List.of()));

        List<FieldErrorDetail> errors = validateErrors(
                new CreateAssessmentRequest(validSettings(GroupingMode.HARD_SKILLS, false), skills, questions));

        assertThat(errors).extracting(FieldErrorDetail::code)
                .contains("DUPLICATE_SKILL", "UNUSED_SKILL", "NO_SKILL", "ORDER_GAP");
    }

    @Test
    void rejectsQuestionReferencingUnknownSkill() {
        CreateAssessmentRequest request = new CreateAssessmentRequest(
                validSettings(GroupingMode.HARD_SKILLS, false),
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(validQuestion(1, List.of("Rust"))));

        List<FieldErrorDetail> errors = validateErrors(request);
        assertThat(errors).extracting(FieldErrorDetail::code).contains("NO_SKILL", "UNUSED_SKILL");
    }

    @Test
    void rejectsEqualMinAndMaxTimeAndDuplicateQuestionPositions() {
        CreateAssessmentRequest request = new CreateAssessmentRequest(
                validSettings(GroupingMode.HARD_SKILLS, false),
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(
                        new QuestionInput(1, "Q1", new BigDecimal("1.00"), new BigDecimal("0.7000"),
                                60, 60, 1, List.of("Java")),
                        new QuestionInput(1, "Q2", new BigDecimal("1.00"), new BigDecimal("0.7000"),
                                30, 120, 1, List.of("Java"))));

        List<FieldErrorDetail> errors = validateErrors(request);
        assertThat(errors).extracting(FieldErrorDetail::code).contains("TIME_RANGE", "ORDER_GAP");
    }

    @Test
    void rejectsCombinedEmptyCombinedWithoutBfi10SoftWithUnusedSkillAndBadCustomWeights() {
        List<FieldErrorDetail> combinedEmpty = validateErrors(new CreateAssessmentRequest(
                validSettings(GroupingMode.COMBINED, true), List.of(), List.of()));
        assertThat(combinedEmpty).extracting(FieldErrorDetail::code).contains("EMPTY_CONFIG");

        List<FieldErrorDetail> combinedNoBfi = validateErrors(new CreateAssessmentRequest(
                validSettings(GroupingMode.COMBINED, false),
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(validQuestion(1, List.of("Java")))));
        // Decision 2.27: COMBINED needs BFI-10 too, so every COMBINED team has a trait term.
        assertThat(combinedNoBfi).extracting(FieldErrorDetail::code).containsExactly("BFI10_REQUIRED");
        assertThat(combinedNoBfi).extracting(FieldErrorDetail::field).containsExactly("settings.bfi10Enabled");
        // HARD_SKILLS without BFI-10 stays valid: validRequest() covers it in acceptsValidHardSkillsConfig.

        List<FieldErrorDetail> softUnused = validateErrors(new CreateAssessmentRequest(
                validSettings(GroupingMode.SOFT_SKILLS, true),
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of()));
        assertThat(softUnused).extracting(FieldErrorDetail::code).contains("UNUSED_SKILL");

        SettingsInput badSum = new SettingsInput(
                "Bad Custom Sum",
                "Vision",
                new BigDecimal("0.5000"),
                1,
                true,
                4,
                GroupingMode.COMBINED,
                SoftPreset.CUSTOM,
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                Map.of(
                        "conscientiousness", new BigDecimal("0.20"),
                        "agreeableness", new BigDecimal("0.20"),
                        "neuroticism", new BigDecimal("0.20"),
                        "extraversion", new BigDecimal("0.20"),
                        "openness", new BigDecimal("0.10")));
        List<FieldErrorDetail> badSumErrors = validateErrors(new CreateAssessmentRequest(
                badSum,
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(validQuestion(1, List.of("Java")))));
        assertThat(badSumErrors).extracting(FieldErrorDetail::code).contains("BAD_WEIGHTS");
    }

    private List<FieldErrorDetail> validateErrors(CreateAssessmentRequest request) {
        Throwable caught = null;
        try {
            validator.validateOrThrow(request);
        } catch (FieldValidationException ex) {
            caught = ex;
        }
        assertThat(caught).isInstanceOf(FieldValidationException.class);
        return ((FieldValidationException) caught).getErrors();
    }

    private static CreateAssessmentRequest validRequest() {
        return new CreateAssessmentRequest(
                validSettings(GroupingMode.HARD_SKILLS, false),
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(validQuestion(1, List.of("Java"))));
    }

    private static SettingsInput validSettings(GroupingMode mode, boolean bfi10) {
        return new SettingsInput(
                "SE Assessment",
                "Evaluate backend craft",
                new BigDecimal("0.6000"),
                1,
                bfi10,
                4,
                mode,
                SoftPreset.ENGINEERING_DEFAULT,
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                Map.of());
    }

    private static QuestionInput validQuestion(int position, List<String> skillNames) {
        return new QuestionInput(
                position,
                "Describe transaction isolation.",
                new BigDecimal("1.50"),
                new BigDecimal("0.7000"),
                30,
                180,
                2,
                skillNames);
    }
}
