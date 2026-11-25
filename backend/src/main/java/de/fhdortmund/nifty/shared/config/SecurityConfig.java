package de.fhdortmund.nifty.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decision 1.23: server sessions for both roles, CSRF through the {@code XSRF-TOKEN} cookie and the
 * {@code X-XSRF-TOKEN} header, and a 401 ProblemDetail instead of a Basic challenge. Imports no feature slice.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String LOGIN = "/api/v1/auth/login";
    static final String LOGOUT = "/api/v1/auth/logout";
    private static final String UNAUTHORIZED_DETAIL = "Sign in to use this resource.";

    // Decision 1.24: DelegatingPasswordEncoder stores the algorithm id ({bcrypt}...) with each hash.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) throws Exception {
        return http
                // CookieCsrfTokenRepository.withHttpOnlyFalse() + the SPA request handler (XSRF-TOKEN / X-XSRF-TOKEN).
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // The SPA never needs a saved request; without a cache, a 401 creates no session.
                .requestCache(RequestCacheConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        (request, response, ex) -> writeUnauthorized(json, request, response, ex)))
                // REQ-AA-06: POST only (CSRF is on), invalidates the session, answers 204.
                .logout(logout -> logout
                        .logoutUrl(LOGOUT)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/v1/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN, LOGOUT, "/api/v1/auth/invite/**").permitAll()
                        // Decision 1.15 / REQ-AA-01: each view's routes allow only its role.
                        .requestMatchers("/api/v1/ranking/**").hasRole(Role.PROFESSOR.name())
                        .requestMatchers("/api/v1/assessment/**").hasRole(Role.CANDIDATE.name())
                        .anyRequest().authenticated()
                )
                .build();
    }

    // docs/api.md §3 envelope. A Map, not ProblemDetail, because this runs outside Spring MVC's message converters.
    private static void writeUnauthorized(JsonMapper json, HttpServletRequest request, HttpServletResponse response,
            AuthenticationException ex) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("detail", UNAUTHORIZED_DETAIL);
        body.put("instance", request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
    }

    /**
     * The CSRF token is deferred: without a read, the response sets no {@code XSRF-TOKEN} cookie. This filter reads it
     * on every request, so the SPA has a token before its first POST (for example the login).
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
