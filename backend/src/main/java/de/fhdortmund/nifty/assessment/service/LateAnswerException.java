package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** REQ-AC-08: 409 for an answer after expiresAt + grace. AttemptService commits the timeout before it throws. */
class LateAnswerException extends DomainException {

    LateAnswerException() {
        super(HttpStatus.CONFLICT, "The time for this question is over. The answer was not stored.");
    }
}
