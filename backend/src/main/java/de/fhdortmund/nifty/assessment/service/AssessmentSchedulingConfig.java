package de.fhdortmund.nifty.assessment.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@link AttemptExpiryScheduler}. Boot's auto-configured scheduler has one thread (bounded). */
@Configuration
@EnableScheduling
class AssessmentSchedulingConfig {
}
