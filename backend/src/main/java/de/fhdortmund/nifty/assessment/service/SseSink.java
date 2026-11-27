package de.fhdortmund.nifty.assessment.service;

/** Writes one SSE event. The web layer serialises {@code data} as JSON; an I/O failure is unchecked. */
@FunctionalInterface
public interface SseSink {

    void send(String event, Object data);
}
