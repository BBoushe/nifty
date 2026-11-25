# Design: Studio Assessment Creation (`studio`)

> **Canonical Contract Reference:** `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`, `docs/tickets/01-studio.md`.

## Package Layout (`de.fhdortmund.nifty.studio`)
- `web/`: `StudioConfigController` (`/api/v1/ranking/studio/configs`), `@Valid` record DTOs (`CreateAssessmentRequest`, `SettingsInput`, `SkillInput`, `QuestionInput`, `AssessmentSummaryResponse`, `AssessmentDetailResponse`).
- `service/`: `ConfigCreationService`, `AssessmentReadService`, `AssessmentDeletionService`, `ConfigValidator` (`SettingsRules`, `SkillRules`, `QuestionRules`), `StudioQueryService` (public interface), `StudioQueryServiceImpl` (package-private), `service/view/*` (`InterviewerView`, `InterviewerQuestion`, `EvaluatorView`, `EvaluatorSkill`, `EvaluatorQuestion`, `ScoringView`, `ScoringQuestion`, `TeamView`).
- `repository/`: **ALL `@Entity` classes and package-private `Repository<T, UUID>` interfaces live here** (`AssessmentConfigEntity`, `SkillEntity`, `CoreQuestionEntity`, `AssessmentConfigRepository`, etc.) to satisfy `ArchitectureRules.java` (Decision 1.10). Mark entities `@Immutable` with no public setters.

## Flyway Migration (`V3__studio.sql`)
- `assessment_config(id UUID PK, professor_id UUID NOT NULL REFERENCES professor_account(id), title TEXT, vision TEXT, min_coverage NUMERIC(5,4) CHECK (0..1), max_attempts INT DEFAULT 1 CHECK (>=1), invented_questions BOOLEAN DEFAULT FALSE, bfi10_enabled BOOLEAN DEFAULT FALSE, team_size INT CHECK (>=2), grouping_mode VARCHAR(16) CHECK IN ('HARD_SKILLS','SOFT_SKILLS','COMBINED'), soft_preset VARCHAR(32) CHECK IN ('ENGINEERING_DEFAULT','EQUAL_WEIGHTS','CUSTOM'), combined_hard_weight NUMERIC(5,4) CHECK (0..1), alpha NUMERIC(5,4) CHECK (0..1), constraint_weights JSONB, status VARCHAR(16) DEFAULT 'PUBLISHED' CHECK (status='PUBLISHED'), version INT DEFAULT 1 CHECK (version=1), idempotency_key UUID NOT NULL UNIQUE, created_at TIMESTAMPTZ)`
- `skill(id UUID PK, config_id UUID FK, name TEXT, rubric_levels JSONB CHECK array length 5)` + unique index `(config_id, lower(trim(name)))`. **No `weight` column** (Decision 2.22).
- `core_question(id UUID PK, config_id UUID FK, position INT >= 1, text TEXT, weight NUMERIC(6,2) > 0, confidence_threshold NUMERIC(5,4) CHECK (0..1), min_time_sec INT > 0, max_time_sec INT, follow_up_limit INT CHECK (0..3), CHECK (min_time_sec < max_time_sec), UNIQUE (config_id, position))`
- `core_question_skill(core_question_id UUID FK, skill_id UUID FK, PRIMARY KEY (core_question_id, skill_id))`
- Trigger `studio_block_mutation()` on all 4 tables rejecting `UPDATE` and rejecting `DELETE` unless `current_setting('nifty.allow_purge', true) = 'on'`.

## Error & Security Rules
- All validation errors throw `FieldValidationException` from `shared/exception/` → `400` RFC 9457 `ProblemDetail` (`errors: [{ field, message, code }]`). Never import `studio` inside `shared`.
- `DELETE /api/v1/ranking/studio/configs/{configId}` verifies professor ownership (`404` otherwise), runs `SET LOCAL nifty.allow_purge = 'on'`, publishes `AssessmentDeletedEvent(configId, actorId)`, deletes studio rows, and writes one `CONFIG_DELETED` audit row via `AuditService`.
