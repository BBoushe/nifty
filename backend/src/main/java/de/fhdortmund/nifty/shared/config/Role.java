package de.fhdortmund.nifty.shared.config;

/**
 * Application roles, one per view (Decision 1.15):
 * - Ranking View: PROFESSOR (also does all review work)
 * - Assessment View: CANDIDATE
 * To add a role, add a value here and its rules in SecurityConfig (or @PreAuthorize).
 */
public enum Role {
    PROFESSOR,
    CANDIDATE
}
