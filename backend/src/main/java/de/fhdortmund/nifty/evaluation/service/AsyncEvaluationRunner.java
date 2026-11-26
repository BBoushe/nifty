package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
import de.fhdortmund.nifty.assessment.service.TranscriptEntryView;
import de.fhdortmund.nifty.context.service.dto.JudgeContextInput;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunEntity;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorQuestion;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-EP-05, REQ-EP-06, REQ-EP-07, REQ-EP-09 / Decisions 2.10, 2.20, 2.22, 2.24:
 * Asynchronous, idempotent evaluation pipeline orchestrator. Executes LLM judge calls outside
 * DB write transactions and commits summary + response evaluations + skill scores atomically
 * so failures never leave partial {@code skill_score} rows.
 */
@Service
@Transactional(readOnly = true)
public class AsyncEvaluationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncEvaluationRunner.class);
    private static final int MAX_ERROR_LEN = 1000;

    private final StudioQueryService studioQueryService;
    private final AssessmentQueryService assessmentQueryService;
    private final TwoRunResponseJudge judge;
    private final VerbatimQuoteVerifier quoteVerifier;
    private final ConfidenceCalculator confidenceCalculator;
    private final SkillFormulaCalculator formulaCalculator;
    private final ReleaseGateEvaluator releaseGateEvaluator;
    private final FormulaVersionRepository formulaVersionRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final CandidateEvaluationSummaryRepository summaryRepository;
    private final ResponseEvaluationRepository responseEvaluationRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final TransactionTemplate transactionTemplate;

    AsyncEvaluationRunner(
            StudioQueryService studioQueryService,
            AssessmentQueryService assessmentQueryService,
            TwoRunResponseJudge judge,
            VerbatimQuoteVerifier quoteVerifier,
            ConfidenceCalculator confidenceCalculator,
            SkillFormulaCalculator formulaCalculator,
            ReleaseGateEvaluator releaseGateEvaluator,
            FormulaVersionRepository formulaVersionRepository,
            EvaluationRunRepository evaluationRunRepository,
            CandidateEvaluationSummaryRepository summaryRepository,
            ResponseEvaluationRepository responseEvaluationRepository,
            SkillScoreRepository skillScoreRepository,
            TransactionTemplate transactionTemplate) {
        this.studioQueryService = studioQueryService;
        this.assessmentQueryService = assessmentQueryService;
        this.judge = judge;
        this.quoteVerifier = quoteVerifier;
        this.confidenceCalculator = confidenceCalculator;
        this.formulaCalculator = formulaCalculator;
        this.releaseGateEvaluator = releaseGateEvaluator;
        this.formulaVersionRepository = formulaVersionRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.summaryRepository = summaryRepository;
        this.responseEvaluationRepository = responseEvaluationRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Async(EvaluationAsyncConfig.EVALUATION_EXECUTOR)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runAsync(AttemptCompletedEvent event) {
        executeEvaluation(event);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void executeEvaluation(AttemptCompletedEvent event) {
        RunClaim claim = claimEvaluationRun(event);
        if (claim == null) {
            return;
        }
        EvaluationRunEntity run = claim.run();
        FormulaVersionEntity formulaVersion = claim.formulaVersion();

        try {
            EvaluatorView evaluatorView = studioQueryService.evaluatorView(event.configId());
            ScoringView scoringView = studioQueryService.scoringView(event.configId());
            ImmutableTranscriptView transcript = assessmentQueryService.getTranscriptForAttempt(event.attemptId());

            Map<UUID, EvaluatorSkill> skillsById = indexSkills(evaluatorView.skills());
            Map<UUID, ScoringQuestion> scoringByQuestion = indexScoringQuestions(scoringView.questions());
            Map<UUID, EvaluatorQuestion> evalByQuestion = indexEvaluatorQuestions(evaluatorView.questions());

            List<EvaluatedPair> evaluatedPairs = new ArrayList<>();
            Set<UUID> answeredCoreQuestionIds = new HashSet<>();
            String templateVersion = null;

            List<TranscriptEntryView> entries = transcript.entries() == null ? List.of() : transcript.entries();
            for (int i = 0; i < entries.size(); i++) {
                TranscriptEntryView entry = entries.get(i);
                // Decision 2.20 / REQ-EP-06: skip ONLY blank/null answers; non-blank timedOut entries are evaluated.
                if (entry.answerText() == null || entry.answerText().isBlank()) {
                    continue;
                }
                if (entry.coreQuestionId() != null) {
                    answeredCoreQuestionIds.add(entry.coreQuestionId());
                }

                ScoringQuestion scoringQ = scoringByQuestion.get(entry.coreQuestionId());
                if (scoringQ == null) {
                    continue;
                }
                List<UUID> targetSkills = resolveTargetSkills(entry, evalByQuestion.get(entry.coreQuestionId()));
                boolean substantive = quoteVerifier.isSubstantiveAnswer(entry.answerText());
                int answerLength = entry.answerText().trim().length();

                for (int s = 0; s < targetSkills.size(); s++) {
                    UUID skillId = targetSkills.get(s);
                    EvaluatorSkill skill = skillsById.get(skillId);
                    if (skill == null) {
                        continue;
                    }
                    JudgeContextInput judgeInput = new JudgeContextInput(
                            event.attemptId(),
                            entry.entryId(),
                            skillId,
                            skill.name(),
                            skill.rubricLevels(),
                            entry.questionText(),
                            entry.answerText(),
                            entry.timedOut());
                    TwoRunResponseJudge.TwoRunResult twoRun = judge.judge(judgeInput);
                    if (templateVersion == null && twoRun.templateVersion() != null) {
                        templateVersion = twoRun.templateVersion();
                    }
                    evaluatedPairs.add(buildEvaluatedPair(entry, scoringQ, skillId, substantive, answerLength, twoRun));
                }
            }

            List<UUID> configuredSkillIds = extractSkillIds(evaluatorView.skills());
            List<SkillFormulaCalculator.QuestionWeight> questionWeights = extractQuestionWeights(scoringView.questions());
            List<SkillFormulaCalculator.ScoredEntryInput> formulaInputs = toFormulaInputs(evaluatedPairs);

            SkillFormulaCalculator.FormulaResult formulaResult = formulaCalculator.calculate(
                    configuredSkillIds,
                    questionWeights,
                    formulaInputs,
                    answeredCoreQuestionIds,
                    formulaVersion.getMaxEntrySkillWeightShare());

            List<ReleaseStatus> pairStatuses = new ArrayList<>(evaluatedPairs.size());
            for (int i = 0; i < evaluatedPairs.size(); i++) {
                pairStatuses.add(evaluatedPairs.get(i).flagEvaluation().reviewStatus());
            }
            ReleaseGateEvaluator.AttemptReleaseDecision releaseDecision = releaseGateEvaluator.evaluateAttemptRelease(
                    pairStatuses, formulaResult.coverage(), scoringView.minCoverage());

            final String finalTemplateVersion = templateVersion;
            final int attemptNo = Math.max(1, transcript.attemptNo());

            // Atomic write: summary + response evaluations + skill scores + COMPLETED status commit together.
            transactionTemplate.executeWithoutResult(tx -> {
                Instant now = Instant.now();
                Instant releasedAt = releaseDecision.autoReleased() ? now : null;
                String summaryStatus = releaseDecision.releaseStatus().literal();

                CandidateEvaluationSummaryEntity summary = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                        event.attemptId(),
                        event.configId(),
                        event.inviteId(),
                        attemptNo,
                        formulaVersion,
                        run,
                        "COMPLETED",
                        formulaResult.fitScore(),
                        formulaResult.coverage(),
                        releaseDecision.flaggedCount(),
                        summaryStatus,
                        releasedAt));

                List<ResponseEvaluationEntity> responseEntities = new ArrayList<>(evaluatedPairs.size());
                for (int i = 0; i < evaluatedPairs.size(); i++) {
                    EvaluatedPair pair = evaluatedPairs.get(i);
                    String responseStatus = releaseDecision.autoReleased()
                            ? ReleaseStatus.AUTO_RELEASED.literal()
                            : pair.flagEvaluation().reviewStatus().literal();
                    responseEntities.add(new ResponseEvaluationEntity(
                            run,
                            summary,
                            pair.entryId(),
                            pair.coreQuestionId(),
                            pair.skillId(),
                            pair.origin().literal(),
                            pair.questionWeight(),
                            pair.run1Level(),
                            pair.run2Level(),
                            pair.run1Quote(),
                            pair.run2Quote(),
                            pair.quoteVerified(),
                            pair.verifiedQuote(),
                            pair.rationale(),
                            pair.aiLevel(),
                            pair.confidence(),
                            pair.flagEvaluation().flagReasons(),
                            responseStatus,
                            pair.effectiveLevel()));
                }
                responseEvaluationRepository.saveAll(responseEntities);

                List<SkillScoreEntity> skillScoreEntities = new ArrayList<>(formulaResult.skillScores().size());
                for (int i = 0; i < formulaResult.skillScores().size(); i++) {
                    SkillFormulaCalculator.CalculatedSkillScore sc = formulaResult.skillScores().get(i);
                    String scoreStatus = releaseDecision.autoReleased()
                            ? ReleaseStatus.AUTO_RELEASED.literal()
                            : sc.reviewStatus();
                    skillScoreEntities.add(new SkillScoreEntity(
                            summary,
                            formulaVersion,
                            sc.skillId(),
                            sc.status().literal(),
                            sc.score(),
                            sc.avgConfidence(),
                            sc.verifiedEvidenceCount(),
                            scoreStatus));
                }
                skillScoreRepository.saveAll(skillScoreEntities);

                run.markCompleted(finalTemplateVersion, now);
                evaluationRunRepository.save(run);
            });
        } catch (RuntimeException ex) {
            LOG.error("Evaluation failed for attempt {}", event.attemptId(), ex);
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (msg.length() > MAX_ERROR_LEN) {
                msg = msg.substring(0, MAX_ERROR_LEN);
            }
            final String finalMsg = msg;
            transactionTemplate.executeWithoutResult(tx -> {
                run.markFailed(finalMsg, Instant.now());
                evaluationRunRepository.save(run);
            });
        }
    }

    private RunClaim claimEvaluationRun(AttemptCompletedEvent event) {
        FormulaVersionEntity formula = ensureFormulaVersion(event.configId());
        try {
            return transactionTemplate.execute(tx -> {
                String idempotencyKey = event.attemptId() + ":" + formula.getId();
                if (evaluationRunRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
                    return null;
                }
                EvaluationRunEntity run = evaluationRunRepository.save(new EvaluationRunEntity(
                        event.attemptId(), event.configId(), formula, null, "RUNNING", idempotencyKey));
                return new RunClaim(run, formula);
            });
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate AttemptCompletedEvent lost the unique idempotency_key race (REQ-EP-09).
            return null;
        }
    }

    private FormulaVersionEntity ensureFormulaVersion(UUID configId) {
        try {
            return transactionTemplate.execute(tx -> formulaVersionRepository
                    .findTopByConfigIdOrderByVersionNumberDesc(configId)
                    .orElseGet(() -> createDefaultFormulaVersion(configId)));
        } catch (DataIntegrityViolationException ex) {
            // Two different attempts finished concurrently on a brand-new config; re-read winning v1.
            return transactionTemplate.execute(tx -> formulaVersionRepository
                    .findTopByConfigIdOrderByVersionNumberDesc(configId)
                    .orElseThrow(() -> ex));
        }
    }

    private FormulaVersionEntity createDefaultFormulaVersion(UUID configId) {
        ScoringView scoringView = studioQueryService.scoringView(configId);
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        for (int i = 0; i < scoringView.questions().size(); i++) {
            ScoringQuestion q = scoringView.questions().get(i);
            weights.put(q.coreQuestionId().toString(), q.weight());
        }
        return formulaVersionRepository.save(new FormulaVersionEntity(
                configId,
                1,
                "WEIGHTED_MEAN",
                SkillFormulaCalculator.DEFAULT_MAX_ENTRY_SHARE,
                weights,
                null));
    }

    private EvaluatedPair buildEvaluatedPair(
            TranscriptEntryView entry,
            ScoringQuestion scoringQ,
            UUID skillId,
            boolean substantive,
            int answerLength,
            TwoRunResponseJudge.TwoRunResult twoRun) {

        TwoRunResponseJudge.ParsedRun r1 = twoRun.run1();
        TwoRunResponseJudge.ParsedRun r2 = twoRun.run2();

        boolean r1Verified = r1.valid() && quoteVerifier.isVerified(entry.answerText(), r1.verbatimQuote());
        boolean r2Verified = r2.valid() && quoteVerifier.isVerified(entry.answerText(), r2.verbatimQuote());
        int verifiedCount = (r1Verified ? 1 : 0) + (r2Verified ? 1 : 0);
        boolean quoteVerified = verifiedCount > 0;
        boolean allQuotesVerified = r1Verified && r2Verified;

        // REQ-EP-01: only verified runs may contribute quote, rationale, or level to scoring.
        String verifiedQuote = r1Verified ? r1.verbatimQuote() : (r2Verified ? r2.verbatimQuote() : null);
        String rationale = r1Verified ? r1.rationale() : (r2Verified ? r2.rationale() : null);
        Integer aiLevel = resolveVerifiedAiLevel(r1, r1Verified, r2, r2Verified);
        Integer effectiveLevel = quoteVerified ? aiLevel : null;

        EntryOrigin origin = entry.origin() == null ? EntryOrigin.CORE : entry.origin();
        int[] termCounts = countPromptedAndUnpromptedTerms(entry.questionText(), entry.answerText());
        BigDecimal confidence = confidenceCalculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                verifiedCount, 2, r1.level(), r2.level(), origin, answerLength, termCounts[0], termCounts[1]));

        // REQ-EP-08: any invalid/malformed judge run is recorded as a parse failure and flagged.
        boolean parseFailed = !r1.valid() || !r2.valid();
        ReleaseGateEvaluator.FlagEvaluation flagEval = releaseGateEvaluator.evaluateResponseFlags(
                substantive,
                quoteVerified,
                allQuotesVerified,
                r1.level(),
                r2.level(),
                confidence,
                scoringQ.confidenceThreshold(),
                entry.pasteSuspected(),
                entry.injectionSuspected(),
                parseFailed);

        return new EvaluatedPair(
                entry.entryId(),
                entry.coreQuestionId(),
                skillId,
                origin,
                scoringQ.weight(),
                r1.level(),
                r2.level(),
                r1.verbatimQuote(),
                r2.verbatimQuote(),
                quoteVerified,
                verifiedQuote,
                rationale,
                aiLevel,
                effectiveLevel,
                confidence,
                flagEval);
    }

    private static Integer resolveVerifiedAiLevel(
            TwoRunResponseJudge.ParsedRun r1,
            boolean r1Verified,
            TwoRunResponseJudge.ParsedRun r2,
            boolean r2Verified) {
        if (r1Verified && r2Verified) {
            return Math.min(r1.level(), r2.level());
        }
        if (r1Verified) {
            return r1.level();
        }
        if (r2Verified) {
            return r2.level();
        }
        return null;
    }

    /** REQ-EP-03: deterministic prompted vs. unprompted term counts (>= 4-char tokens), zero Streams. */
    static int[] countPromptedAndUnpromptedTerms(String questionText, String answerText) {
        Set<String> questionTokens = extractContentTokens(questionText);
        Set<String> answerTokens = extractContentTokens(answerText);
        int prompted = 0;
        int unprompted = 0;
        for (String token : answerTokens) {
            if (questionTokens.contains(token)) {
                prompted++;
            } else {
                unprompted++;
            }
        }
        return new int[] {prompted, unprompted};
    }

    private static Set<String> extractContentTokens(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                current.append(Character.toLowerCase(ch));
            } else if (current.length() > 0) {
                if (current.length() >= 4) {
                    tokens.add(current.toString());
                }
                current.setLength(0);
            }
        }
        if (current.length() >= 4) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static List<UUID> resolveTargetSkills(TranscriptEntryView entry, EvaluatorQuestion evalQ) {
        if (entry.targetSkillIds() != null && !entry.targetSkillIds().isEmpty()) {
            return entry.targetSkillIds();
        }
        return evalQ == null ? List.of() : evalQ.skillIds();
    }

    private static Map<UUID, EvaluatorSkill> indexSkills(List<EvaluatorSkill> skills) {
        Map<UUID, EvaluatorSkill> map = new HashMap<>(skills.size());
        for (int i = 0; i < skills.size(); i++) {
            EvaluatorSkill skill = skills.get(i);
            map.put(skill.skillId(), skill);
        }
        return map;
    }

    private static Map<UUID, ScoringQuestion> indexScoringQuestions(List<ScoringQuestion> questions) {
        Map<UUID, ScoringQuestion> map = new HashMap<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            ScoringQuestion q = questions.get(i);
            map.put(q.coreQuestionId(), q);
        }
        return map;
    }

    private static Map<UUID, EvaluatorQuestion> indexEvaluatorQuestions(List<EvaluatorQuestion> questions) {
        Map<UUID, EvaluatorQuestion> map = new HashMap<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            EvaluatorQuestion q = questions.get(i);
            map.put(q.coreQuestionId(), q);
        }
        return map;
    }

    private static List<UUID> extractSkillIds(List<EvaluatorSkill> skills) {
        List<UUID> ids = new ArrayList<>(skills.size());
        for (int i = 0; i < skills.size(); i++) {
            ids.add(skills.get(i).skillId());
        }
        return ids;
    }

    private static List<SkillFormulaCalculator.QuestionWeight> extractQuestionWeights(List<ScoringQuestion> questions) {
        List<SkillFormulaCalculator.QuestionWeight> weights = new ArrayList<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            ScoringQuestion q = questions.get(i);
            weights.add(new SkillFormulaCalculator.QuestionWeight(q.coreQuestionId(), q.weight()));
        }
        return weights;
    }

    private static List<SkillFormulaCalculator.ScoredEntryInput> toFormulaInputs(List<EvaluatedPair> pairs) {
        List<SkillFormulaCalculator.ScoredEntryInput> inputs = new ArrayList<>(pairs.size());
        for (int i = 0; i < pairs.size(); i++) {
            EvaluatedPair p = pairs.get(i);
            inputs.add(new SkillFormulaCalculator.ScoredEntryInput(
                    p.entryId(),
                    p.coreQuestionId(),
                    p.skillId(),
                    p.origin(),
                    p.questionWeight(),
                    p.effectiveLevel(),
                    p.confidence(),
                    p.quoteVerified(),
                    false,
                    p.flagEvaluation().reviewStatus().literal()));
        }
        return inputs;
    }

    private record RunClaim(EvaluationRunEntity run, FormulaVersionEntity formulaVersion) {
    }

    private record EvaluatedPair(
            UUID entryId,
            UUID coreQuestionId,
            UUID skillId,
            EntryOrigin origin,
            BigDecimal questionWeight,
            Integer run1Level,
            Integer run2Level,
            String run1Quote,
            String run2Quote,
            boolean quoteVerified,
            String verifiedQuote,
            String rationale,
            Integer aiLevel,
            Integer effectiveLevel,
            BigDecimal confidence,
            ReleaseGateEvaluator.FlagEvaluation flagEvaluation
    ) {
    }
}
