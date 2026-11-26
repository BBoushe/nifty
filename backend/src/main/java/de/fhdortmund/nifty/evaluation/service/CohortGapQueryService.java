package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-SM-04 / Decision 2.13: SQL-only Cohort Gap Overview over released latest completed attempts.
 * Zero LLM calls and zero Java-side cohort aggregation.
 */
@Service
@Transactional(readOnly = true)
public class CohortGapQueryService {

    private static final String CONFIG_NOT_FOUND = "Assessment configuration not found.";

    private static final String RELEASED_COUNT_SQL = """
            WITH latest_attempts AS (
                SELECT DISTINCT ON (invite_id) id, release_status
                FROM candidate_evaluation_summary
                WHERE config_id = ?
                  AND evaluation_status = 'COMPLETED'
                ORDER BY invite_id, attempt_no DESC, created_at DESC
            )
            SELECT COUNT(*)
            FROM latest_attempts
            WHERE release_status IN ('auto_released', 'approved', 'overridden')
            """;

    private static final String SKILL_GAPS_SQL = """
            WITH latest_attempts AS (
                SELECT DISTINCT ON (invite_id) id, release_status
                FROM candidate_evaluation_summary
                WHERE config_id = ?
                  AND evaluation_status = 'COMPLETED'
                ORDER BY invite_id, attempt_no DESC, created_at DESC
            ),
            released_latest AS (
                SELECT id
                FROM latest_attempts
                WHERE release_status IN ('auto_released', 'approved', 'overridden')
            )
            SELECT
                ss.skill_id,
                COUNT(*)::int AS total_candidates,
                COUNT(*) FILTER (WHERE ss.status = 'SCORED')::int AS scored_count,
                COUNT(*) FILTER (WHERE ss.status = 'N/A')::int AS na_count,
                ROUND(
                    CASE WHEN COUNT(*) = 0 THEN 0
                         ELSE COUNT(*) FILTER (WHERE ss.status = 'N/A')::numeric / COUNT(*)::numeric
                    END, 4
                ) AS na_share,
                ROUND(AVG(ss.score) FILTER (WHERE ss.status = 'SCORED'), 2) AS mean_score,
                COUNT(*) FILTER (WHERE ss.status = 'SCORED' AND ROUND(ss.score) = 0)::int AS level_0,
                COUNT(*) FILTER (WHERE ss.status = 'SCORED' AND ROUND(ss.score) = 1)::int AS level_1,
                COUNT(*) FILTER (WHERE ss.status = 'SCORED' AND ROUND(ss.score) = 2)::int AS level_2,
                COUNT(*) FILTER (WHERE ss.status = 'SCORED' AND ROUND(ss.score) = 3)::int AS level_3,
                COUNT(*) FILTER (WHERE ss.status = 'SCORED' AND ROUND(ss.score) = 4)::int AS level_4
            FROM skill_score ss
            JOIN released_latest rl ON ss.summary_id = rl.id
            GROUP BY ss.skill_id
            ORDER BY mean_score ASC NULLS LAST
            """;

    private final JdbcTemplate jdbc;
    private final StudioQueryService studioQueryService;
    private final AuthContextService authContextService;

    CohortGapQueryService(
            JdbcTemplate jdbc,
            StudioQueryService studioQueryService,
            AuthContextService authContextService) {
        this.jdbc = jdbc;
        this.studioQueryService = studioQueryService;
        this.authContextService = authContextService;
    }

    public EvaluationDtos.CohortGapsResponse getCohortGaps(UUID configId) {
        UUID professorId = authContextService.currentProfessorId();
        if (configId == null || !studioQueryService.ownedBy(configId, professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, CONFIG_NOT_FOUND);
        }

        EvaluatorView evaluatorView = studioQueryService.evaluatorView(configId);
        Map<UUID, String> skillNames = new HashMap<>(evaluatorView.skills().size());
        for (int i = 0; i < evaluatorView.skills().size(); i++) {
            EvaluatorSkill s = evaluatorView.skills().get(i);
            skillNames.put(s.skillId(), s.name());
        }

        Integer releasedCountRaw = jdbc.queryForObject(RELEASED_COUNT_SQL, Integer.class, configId);
        int releasedCount = releasedCountRaw == null ? 0 : releasedCountRaw;

        List<EvaluationDtos.SkillGapStat> stats = jdbc.query(
                SKILL_GAPS_SQL,
                (rs, rowNum) -> {
                    UUID skillId = rs.getObject("skill_id", UUID.class);
                    return new EvaluationDtos.SkillGapStat(
                            skillId,
                            skillNames.getOrDefault(skillId, skillId.toString()),
                            rs.getInt("total_candidates"),
                            rs.getInt("scored_count"),
                            rs.getInt("na_count"),
                            rs.getBigDecimal("na_share"),
                            rs.getBigDecimal("mean_score"),
                            List.of(
                                    rs.getInt("level_0"),
                                    rs.getInt("level_1"),
                                    rs.getInt("level_2"),
                                    rs.getInt("level_3"),
                                    rs.getInt("level_4")));
                },
                configId);

        // Include configured skills with 0 released rows if the cohort has no released attempts yet.
        if (stats.isEmpty() && !evaluatorView.skills().isEmpty()) {
            List<EvaluationDtos.SkillGapStat> emptyStats = new ArrayList<>(evaluatorView.skills().size());
            for (int i = 0; i < evaluatorView.skills().size(); i++) {
                EvaluatorSkill s = evaluatorView.skills().get(i);
                emptyStats.add(new EvaluationDtos.SkillGapStat(
                        s.skillId(), s.name(), 0, 0, 0,
                        BigDecimal.ZERO.setScale(4), null, List.of(0, 0, 0, 0, 0)));
            }
            return new EvaluationDtos.CohortGapsResponse(configId, releasedCount, List.copyOf(emptyStats));
        }

        return new EvaluationDtos.CohortGapsResponse(configId, releasedCount, List.copyOf(stats));
    }
}
