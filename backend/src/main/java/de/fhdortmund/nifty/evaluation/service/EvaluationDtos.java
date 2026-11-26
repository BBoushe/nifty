package de.fhdortmund.nifty.evaluation.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public record DTOs for the evaluation slice (REQ-FE-02..06, REQ-SM-01..08, REQ-CR-01..06).
 * Grouped cleanly to keep the codebase minimal and prevent duplicate DTO classes (Coding Standard #5).
 */
public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    public record SkillColumn(UUID skillId, String skillName) {
    }

    public record MatrixCell(
            UUID skillId,
            String skillName,
            ScoreStatus status,
            BigDecimal value,
            BigDecimal avgConfidence,
            int verifiedEvidenceCount,
            ReleaseStatus reviewStatus
    ) {
    }

    public record MatrixCandidateRow(
            UUID inviteId,
            UUID attemptId,
            int attemptNo,
            BigDecimal fitScore,
            BigDecimal coverage,
            ReleaseStatus releaseStatus,
            int flaggedCount,
            Instant releasedAt,
            List<MatrixCell> cells
    ) {
    }

    public record MatrixResponse(
            UUID configId,
            UUID formulaVersionId,
            int formulaVersionNumber,
            String formulaPreset,
            BigDecimal minCoverage,
            List<SkillColumn> skills,
            List<MatrixCandidateRow> rows
    ) {
    }

    public record ResponseEvaluationDetail(
            UUID responseEvaluationId,
            UUID entryId,
            UUID coreQuestionId,
            UUID skillId,
            String skillName,
            String questionOrigin,
            BigDecimal questionWeight,
            BigDecimal confidenceThreshold,
            Integer run1Level,
            Integer run2Level,
            String run1Quote,
            String run2Quote,
            boolean quoteVerified,
            String verifiedQuote,
            String aiRationale,
            Integer aiLevel,
            BigDecimal calculatedConfidence,
            List<String> flagReasons,
            ReleaseStatus reviewStatus,
            Integer effectiveLevel,
            UUID overriddenBy,
            String overrideReason,
            Instant reviewedAt
    ) {
    }

    public record AttemptEvaluationDrilldownResponse(
            UUID attemptId,
            UUID configId,
            UUID inviteId,
            int attemptNo,
            BigDecimal fitScore,
            BigDecimal coverage,
            BigDecimal minCoverage,
            ReleaseStatus releaseStatus,
            int flaggedCount,
            Instant releasedAt,
            List<ResponseEvaluationDetail> responses,
            List<MatrixCell> skillScores
    ) {
    }

    public record BulkApproveRequest(UUID configId, List<UUID> responseEvaluationIds) {
    }

    public record BulkApproveResponse(int approvedCount, int skippedFlaggedCount) {
    }

    public record ApproveEvaluationResponse(
            UUID responseEvaluationId,
            UUID attemptId,
            Integer effectiveLevel,
            ReleaseStatus reviewStatus,
            Instant reviewedAt,
            int remainingFlaggedCount
    ) {
    }

    public record OverrideEvaluationRequest(Integer newLevel, String reason) {
    }

    public record OverrideEvaluationResponse(
            UUID responseEvaluationId,
            UUID attemptId,
            Integer aiLevel,
            Integer effectiveLevel,
            ReleaseStatus reviewStatus,
            String overrideReason,
            Instant reviewedAt,
            BigDecimal updatedFitScore,
            BigDecimal updatedCoverage,
            int remainingFlaggedCount
    ) {
    }

    public record ReleaseAttemptResponse(
            UUID attemptId,
            ReleaseStatus releaseStatus,
            Instant releasedAt,
            BigDecimal fitScore,
            BigDecimal coverage
    ) {
    }

    public record SkillGapStat(
            UUID skillId,
            String skillName,
            int totalCandidates,
            int scoredCount,
            int naCount,
            BigDecimal naShare,
            BigDecimal meanScore,
            List<Integer> levelDistribution // index 0..4 counts
    ) {
    }

    public record CohortGapsResponse(
            UUID configId,
            int releasedCandidateCount,
            List<SkillGapStat> skills
    ) {
    }

    public record TranscriptQuestionBlock(
            String text,
            String origin,
            UUID coreQuestionId,
            BigDecimal weight,
            List<UUID> targetSkills,
            Instant askedAt,
            Instant expiresAt
    ) {
    }

    public record TranscriptAnswerBlock(
            String text,
            Instant submittedAt,
            long durationSec,
            boolean timedOut,
            boolean pasteSuspected,
            boolean injectionSuspected
    ) {
    }

    public record TranscriptEntryMetadata(
            List<String> promptedTerms,
            List<String> unpromptedTerms
    ) {
    }

    public record TranscriptExportEntry(
            UUID entryId,
            int turn,
            TranscriptQuestionBlock question,
            TranscriptAnswerBlock answer,
            TranscriptEntryMetadata metadata
    ) {
    }

    public record TranscriptExportEvidence(
            UUID evidenceId,
            UUID entryId,
            UUID skillId,
            String skillName,
            String quote,
            boolean quoteVerified,
            Integer suggestedLevel,
            List<Integer> judgeRuns,
            String rationale
    ) {
    }

    public record TranscriptExportResponse(
            String format,
            UUID transcriptId,
            UUID candidateId,
            UUID configId,
            String configVersion,
            int attempt,
            Instant startedAt,
            List<TranscriptExportEntry> entries,
            List<TranscriptExportEvidence> evidence
    ) {
    }

    public record FormulaPresetInfo(String preset, String label, String description, BigDecimal maxEntrySkillWeightShare) {
    }

    public record FormulaVersionResponse(
            UUID formulaVersionId,
            UUID configId,
            int versionNumber,
            String preset,
            BigDecimal maxEntrySkillWeightShare,
            Map<String, BigDecimal> questionWeights,
            Instant createdAt,
            int recomputedCandidates
    ) {
    }

    public record FormulaPreviewCandidate(
            UUID inviteId,
            UUID attemptId,
            BigDecimal fitScore,
            BigDecimal coverage,
            List<MatrixCell> skillScores
    ) {
    }

    public record FormulaPreviewResponse(
            UUID configId,
            String preset,
            BigDecimal maxEntrySkillWeightShare,
            List<FormulaPreviewCandidate> candidates
    ) {
    }

    public record CandidateAdvice(
            List<String> strengths,
            List<String> improvements,
            List<String> notAssessed
    ) {
    }

    public record CandidateEvidenceItem(
            UUID entryId,
            UUID coreQuestionId,
            UUID skillId,
            String skillName,
            String verifiedQuote,
            Integer level,
            BigDecimal confidence,
            BigDecimal questionWeight
    ) {
    }

    public record CandidateConversationTurn(
            UUID entryId,
            int seq,
            String origin,
            String questionText,
            String answerText,
            Instant askedAt,
            Instant submittedAt,
            boolean timedOut
    ) {
    }

    public record CandidateTraitScore(String trait, double score, String description) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CandidateResultsResponse(
            UUID configId,
            UUID attemptId,
            ReleaseStatus releaseStatus,
            boolean released,
            Instant releasedAt,
            String formulaPreset,
            Integer formulaVersionNumber,
            BigDecimal fitScore,
            BigDecimal coverage,
            List<MatrixCell> skillScores,
            List<CandidateEvidenceItem> evidence,
            Map<String, BigDecimal> questionWeights,
            List<CandidateConversationTurn> conversationLog,
            CandidateAdvice advice,
            List<CandidateTraitScore> softSkillTraits
    ) {
    }
}
