package de.fhdortmund.nifty.auth.web;

import de.fhdortmund.nifty.auth.service.AuthSessionService;
import de.fhdortmund.nifty.auth.service.NiftyPrincipal;
import de.fhdortmund.nifty.shared.config.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Professor sign-in, candidate invite-link sign-in, and session info (Decisions 1.17, 1.23). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthSessionService sessions;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    AuthController(AuthSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/login")
    ResponseEntity<Void> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = sessions.login(body.email(), body.password());
        establishSession(authentication, request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite/{token}")
    InviteSignInResponse inviteLogin(@PathVariable String token, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = sessions.loginWithInvite(token);
        establishSession(authentication, request, response);
        NiftyPrincipal principal = (NiftyPrincipal) authentication.getPrincipal();
        return new InviteSignInResponse(principal.configId());
    }

    @GetMapping("/me")
    MeResponse me() {
        return MeResponse.of(sessions.me());
    }

    private void establishSession(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        // Session fixation: an existing session gets a new ID before it holds the signed-in context.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    record InviteSignInResponse(UUID configId) {
    }

    record MeResponse(Role role, UUID professorId, UUID inviteId, UUID configId) {

        static MeResponse of(NiftyPrincipal principal) {
            if (principal.role() == Role.PROFESSOR) {
                return new MeResponse(Role.PROFESSOR, principal.id(), null, null);
            }
            return new MeResponse(Role.CANDIDATE, null, principal.inviteId(), principal.configId());
        }
    }
}
