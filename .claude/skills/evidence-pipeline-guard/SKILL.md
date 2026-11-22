---
name: evidence-pipeline-guard
description: Audit changes touching assessment transcripts, evidence extraction, quote verification, scoring, confidence, or team formation against non-negotiable invariants.
---

# Skill: Evidence Pipeline Guard

Use this skill whenever reviewing or editing code in the 9-step evidence-first pipeline or `assessment-transcript/v1`.

## Mandatory Audit Checks

1. **No LLM Math:** Search modified files to verify no LLM output field is used directly as a total, fit score, coverage, ranking, or cohort statistic without deterministic Java/SQL calculation.
2. **Verbatim Quote Verification:** Confirm every extracted evidence item is checked word-for-word against its referenced `entryId` in the transcript, and unverified quotes are discarded.
3. **Explicit `N/A`:** Confirm skills with zero verified evidence yield `N/A` (never `0` or `1`) and are excluded from fit denominator/numerator.
4. **Calculated Confidence:** Confirm confidence (`0..1`) is derived solely from deterministic signals (evidence count, quote verification ratio, 2-run judge agreement, question origin, prompted vs. unprompted terms).
5. **Audit Trail & Human Gates:** Confirm score overrides enforce a non-empty reason and persist an audit log entry (`actor`, `timestamp`, `oldValue`, `newValue`, `reason`).
6. **Fresh Context for Team Builder LLM:** Confirm Mode B/C LLM calls use a fresh context and pass deterministic validation (every candidate assigned once, exact team sizes, no invented IDs).
