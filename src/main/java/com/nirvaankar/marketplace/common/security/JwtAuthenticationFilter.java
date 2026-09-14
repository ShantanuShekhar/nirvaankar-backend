package com.nirvaankar.marketplace.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.common.error.ApiErrorResponse;
import com.nirvaankar.marketplace.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads the Bearer token, verifies it, and populates the SecurityContext.
 * <p>
 * Permissions are loaded as authorities so that authorization is written as
 * {@code @PreAuthorize("hasAuthority('payout.approve')")} rather than a role
 * string comparison. Roles are also exposed, prefixed, for the coarse
 * per-panel checks in {@link SecurityConfig}.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthPrincipal principal = jwtService.verifyAndExtract(header.substring(BEARER_PREFIX.length()));
            List<SimpleGrantedAuthority> authorities = Stream.concat(
                            principal.permissions().stream().map(SimpleGrantedAuthority::new),
                            principal.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)))
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put("userId", String.valueOf(principal.userId()));

            filterChain.doFilter(request, response);
        } catch (ApiException e) {
            SecurityContextHolder.clearContext();
            writeError(response, e);
        } finally {
            MDC.remove("userId");
        }
    }

    private void writeError(HttpServletResponse response, ApiException e) throws IOException {
        response.setStatus(e.getErrorCode().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = ApiErrorResponse.of(
                e.getErrorCode(), e.getMessage(), MDC.get("traceId"), List.of());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
