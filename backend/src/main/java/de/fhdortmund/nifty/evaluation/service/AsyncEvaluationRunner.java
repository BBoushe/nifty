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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-EP-05, REQ-EP-06, REQ-EP-07, REQ-EP-09 / Decisions 2.10, 2.13, 2.20, 2.22, 2.24:
 * Asynchronous, idempotent evaluation pipeline orchestrator. {@link #enqueue} persists a QUEUED run and a QUEUED
 * summary before any dispatch, so no completed attempt can vanish. Executes LLM judge calls outside
 * DB write transactions and commits summary + response evaluations + skill scores atomically
 * so failures never leave partial {@code skill_score} rows. FAILED, QUEUED, and stale RUNNING runs are retried
 * with a bounded try count ({@link #retryableRunIds}).
 */
@Service
@Transactional(readOnly = true)
public class AsyncEvaluationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncEvaluationRunner.class);
    private static final int MAX_ERROR_LEN = 1000;
    private static final int RETRY_BATCH = 50;
    private static final String RUNNING = "RUNNING";
    private static final String FAILED = "FAILED";
    private static final String STALE_FINAL_TRY = "STALE_FINAL_TRY";
    // shortcut: a completion lost for longer than this stays lost; widen the window or add a startup sweep if needed.
    private static final Duration RECOVERY_WINDOW = Duration.ofDays(7);

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
    private final int maxAttempts;
    private final Duration staleAfter;

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
            TransactionTemplate transactionTemplate,
            @Value("${nifty.evaluation.retry.max-attempts:3}") int maxAttempts,
            @Value("${nifty.evaluation.retry.stale-after:PT30M}") Duration staleAfter) {
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
        this.maxAttempts = maxAttempts;
        this.staleAfter = staleAfter;
    }

    /**
     * Decision 2.28: persists the QUEUED run and QUEUED summary of a completed attempt BEFORE any dispatch. Each
     * transaction here is its own: this runs on the listener thread right after the publisher committed, where a
     * plain REQUIRED transaction would join the already finished one and silently write nothing.
     *
     * @return the new run id, or null when this attempt is already known (duplicate event, Decision 2.28)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UUID enqueue(AttemptCompletedEvent event) {
        if (summaryRepository.findByAttemptId(event.attemptId()).isPresent()) {
            return null;
        }
        FormulaVersionEntity formula = ensureFormulaVersion(event.configId());
        int attemptNo = Math.max(1, assessmentQueryService.getTranscriptForAttempt(event.attemptId()).attemptNo());
        try {
            return transactionTemplate.execute(tx -> {
                EvaluationRunEntity run = evaluationRunRepository.save(new EvaluationRunEntity(
                        event.attemptId(), event.configId(), formula, null, "QUEUED", event.attemptId().toString()));
                summaryRepository.save(CandidateEvaluationSummaryEntity.queued(
                        event.attemptId(), event.configId(), event.inviteId(), attemptNo, formula, run));
                return run.getId();
            });
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate AttemptCompletedEvent lost the unique attempt_id race (REQ-EP-09).
            return null;
        }
    }

    /**
     * REQ-EP-13: creates the run of every completed attempt whose completion event was lost (crash between the
     * assessment commit and {@link #enqueue}). {@link #enqueue} is idempotent, so a repeated pass adds nothing.
     * shortcut: re-checks every attempt completed inside the window on each pass (one lookup each); upgrade to an
     * anti-join if cohorts grow past a few hundred attempts per week.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recoverMissedRuns() {
        List<AttemptCompletedEvent> completed =
                assessmentQueryService.completedAttemptsSince(Instant.now().minus(RECOVERY_WINDOW));
        for (int i = 0; i < completed.size(); i++) {
            try {
                enqueue(completed.get(i));
            } catch (RuntimeException ex) {
                LOG.warn("Could not recover the evaluation run of attempt {}", completed.get(i).attemptId(), ex);
            }
        }
    }

    /**
     * REQ-EP-11: a stale RUNNING run on its last try can never be claimed again, so it ends as FAILED. The professor
     * can re-run it (REQ-EP-12).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void failStaleFinalTries() {
        List<UUID> runIds = evaluationRunRepository.findStaleFinalTryIds(maxAttempts, Instant.now().minus(staleAfter));
        for (int i = 0; i < runIds.size(); i++) {
            UUID runId = runIds.get(i);
            transactionTemplate.executeWithoutResult(tx -> {
                EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElseThrow();
                CandidateEvaluationSummaryEntity summary =
                        summaryRepository.findByAttemptId(run.getAttemptId()).orElseThrow();
                if (summary.isCompleted() || !RUNNING.equals(run.getStatus())) {
                    return;
                }
                run.markFailed(STALE_FINAL_TRY, Instant.now());
                evaluationRunRepository.save(run);
                summary.markEvaluationStatus(FAILED);
                summaryRepository.save(summary);
            });
        }
    }

    /** Runs the scheduler should dispatch again. Order: oldest first. */
    public List<UUID> retryableRunIds() {
        return evaluationRunRepository.findRetryableIds(
                maxAttempts, Instant.now().minus(staleAfter), PageRequest.ofSize(RETRY_BATCH));
    }

    @Async(EvaluationAsyncConfig.EVALUATION_EXECUTOR)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runAsync(UUID runId) {
        executeRun(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void executeRun(UUID runId) {
        RunClaim claim = claimRun(runId);
        if (claim == null) {
            return;
        }
        FormulaVersionEntity formulaVersion = claim.formulaVersion();
        UUID configId = claim.configId();
        UUID attemptId = claim.attemptId();

        try {
            EvaluatorView evaluatorView = studioQueryService.evaluatorView(configId);
            ScoringView scoringView = studioQueryService.scoringView(configId);
            ImmutableTranscriptView transcript = assessmentQueryService.getTranscriptForAttempt(attemptId);

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
                // Decision 2.22: an invented-question entry never counts toward coverage.
                if (entry.coreQuestionId() != null && entry.origin() != EntryOrigin.INVENTED) {
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
                            attemptId,
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

            // Atomic write: summary + response evaluations + skill scores + COMPLETED status commit together.
            transactionTemplate.executeWithoutResult(tx -> {
                Instant now = Instant.now();
                Instant releasedAt = releaseDecision.autoReleased() ? now : null;
                String summaryStatus = releaseDecision.releaseStatus().literal();

                CandidateEvaluationSummaryEntity summary = summaryRepository.findById(claim.summaryId()).orElseThrow();
                if (summary.isCompleted()) {
                    // A stale-claim twin finished first: never write a second set of rows for one attempt.
                    return;
                }
                EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElseThrow();
                summary.markCompleted(
                        formulaVersion,
                        formulaResult.fitScore(),
                        formulaResult.coverage(),
                        releaseDecision.flaggedCount(),
                        summaryStatus,
                        releasedAt);
                summaryRepository.save(summary);

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
            LOG.error("Evaluation failed for attempt {}", attemptId, ex);
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (msg.length() > MAX_ERROR_LEN) {
                msg = msg.substring(0, MAX_ERROR_LEN);
            }
            markFailed(claim, msg);
        }
    }

    /** Keeps the run retryable (bounded by max attempts); a completed twin is never overwritten. */
    private void markFailed(RunClaim claim, String message) {
        transactionTemplate.executeWithoutResult(tx -> {
            EvaluationRunEntity run = evaluationRunRepository.findById(claim.runId()).orElseThrow();
            CandidateEvaluationSummaryEntity summary = summaryRepository.findById(claim.summaryId()).orElseThrow();
            if (summary.isCompleted() || !RUNNING.equals(run.getStatus())) {
                return;
            }
            run.markFailed(message, Instant.now());
            evaluationRunRepository.save(run);
            summary.markEvaluationStatus(FAILED);
            summaryRepository.save(summary);
        });
    }

    /**
     * Decision 2.28 / REQ-EP-09: one atomic UPDATE decides which worker owns the run, so two workers never
     * evaluate one attempt. shortcut: a worker that outlives {@code staleAfter} can be overtaken by a twin; the
     * completed-twin guards in the final write keep the data single, upgrade to a lease heartbeat if runs get long.
     */
    private RunClaim claimRun(UUID runId) {
        return transactionTemplate.execute(tx -> {
            Instant now = Instant.now();
            if (evaluationRunRepository.claim(runId, now, now.minus(staleAfter), maxAttempts) == 0) {
                return null;
            }
            EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElseThrow();
            // REQ-FE-02: a run that waited while a new formula version appeared scores under the newest version.
            FormulaVersionEntity formula = formulaVersionRepository
                    .findTopByConfigIdOrderByVersionNumberDesc(run.getConfigId())
                    .orElse(run.getFormulaVersion());
            if (!Objects.equals(formula.getId(), run.getFormulaVersion().getId())) {
                run.useFormulaVersion(formula);
            }
            CandidateEvaluationSummaryEntity summary = summaryRepository.findByAttemptId(run.getAttemptId())
                    .orElseThrow();
            summary.markEvaluationStatus(RUNNING);
            return new RunClaim(run.getId(), run.getAttemptId(), run.getConfigId(), summary.getId(), formula);
        });
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
        List<UUID> raw = (entry.targetSkillIds() != null && !entry.targetSkillIds().isEmpty())
                ? entry.targetSkillIds()
                : (evalQ == null ? List.of() : evalQ.skillIds());
        if (raw == null || raw.size() <= 1) {
            return raw == null ? List.of() : raw;
        }
        List<UUID> unique = new ArrayList<>(raw.size());
        Set<UUID> seen = new HashSet<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            UUID id = raw.get(i);
            if (id != null && seen.add(id)) {
                unique.add(id);
            }
        }
        return unique;
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

    private record RunClaim(UUID runId, UUID attemptId, UUID configId, UUID summaryId, FormulaVersionEntity formulaVersion) {
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
