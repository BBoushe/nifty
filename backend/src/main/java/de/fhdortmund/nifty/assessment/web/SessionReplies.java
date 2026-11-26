package de.fhdortmund.nifty.assessment.web;

import de.fhdortmund.nifty.assessment.service.SessionReply;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-AC-05: one return type for both start answers. A declared {@code ResponseEntity<?>} would not reach the
 * streaming handler, so the JSON state is written through the same {@link StreamingResponseBody}.
 */
final class SessionReplies {

    private SessionReplies() {
    }

    static ResponseEntity<StreamingResponseBody> toResponse(SessionReply reply, JsonMapper json) {
        if (reply instanceof SessionReply.State state) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(out -> json.writeValue(out, state.state()));
        }
        SessionReply.Stream stream = (SessionReply.Stream) reply;
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no") // Nginx in front of the backend must not buffer the stream.
                .body(out -> stream.body().accept((event, data) -> write(out, json, event, data)));
    }

    /** JSON keeps every data value on one line, so an SSE {@code data:} field never needs splitting. */
    private static void write(OutputStream out, JsonMapper json, String event, Object data) {
        try {
            out.write(("event: " + event + "\ndata: " + json.writeValueAsString(data) + "\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
