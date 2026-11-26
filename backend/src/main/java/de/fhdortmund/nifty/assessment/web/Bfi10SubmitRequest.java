package de.fhdortmund.nifty.assessment.web;

import java.util.List;

/** REQ-AC-14: 10 answers 1..5 in item order. The service names each missing or invalid item. */
record Bfi10SubmitRequest(List<Integer> items) {
}
