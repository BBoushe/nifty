package de.fhdortmund.nifty.auth.web;

import de.fhdortmund.nifty.auth.service.AuthSessionService;
import de.fhdortmund.nifty.auth.service.NiftyPrincipal;
import de.fhdortmund.nifty.shared.config.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-AA-08 / Decision 1.23: runs after {@code springSecurityFilterChain} (order -100) and rejects
 * every request of a candidate session whose invite was revoked or purged, invalidating the session
 * and returning a 401 RFC 9457 ProblemDetail.
 */
@Component
@Order(0)
class RevokedInviteFilter extends OncePerRequestFilter {

    private static final String INVALID_INVITE_DETAIL = "This link is not valid.";

    private final ObjectProvider<AuthSessionService> sessionsProvider;
    private final JsonMapper json;

    // ObjectProvider lets unrelated @WebMvcTest slices boot cleanly without requiring auth services.
    RevokedInviteFilter(ObjectProvider<AuthSessionService> sessionsProvider, JsonMapper json) {
        this.sessionsProvider = sessionsProvider;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.startsWith("/api/v1/auth/invite/")
                || "/api/v1/auth/login".equals(uri)
                || "/api/v1/auth/logout".equals(uri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuthSessionService sessions = sessionsProvider.getIfAvailable();
        NiftyPrincipal principal = NiftyPrincipal.current().orElse(null);
        if (sessions != null
                && principal != null
                && principal.role() == Role.CANDIDATE
                && principal.inviteId() != null
                && !sessions.isInviteActive(principal.inviteId(), principal.tokenHash())) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            writeUnauthorized(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("detail", INVALID_INVITE_DETAIL);
        body.put("instance", request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
    }
}
