package de.fhdortmund.nifty.studio.repository;

/** One list row: a configuration with its skill and question counts from the same query (Task 16). */
public record ConfigWithCounts(AssessmentConfigEntity config, long skillCount, long questionCount) {
}
