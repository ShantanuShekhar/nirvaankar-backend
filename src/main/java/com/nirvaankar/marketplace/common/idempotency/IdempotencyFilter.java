package com.nirvaankar.marketplace.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.common.error.ApiErrorResponse;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Enforces the Idempotency-Key header on the endpoints that move money or
 * create records a duplicate of which would be expensive.
 * <p>
 * Runs after authentication so the key can be scoped to a user, and wraps the
 * response so a successful body can be cached and replayed verbatim on retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    /**
     * Exact create endpoints only. Webhooks and simulate-capture must not require
     * Idempotency-Key — they use their own duplicate-event handling.
     */
    private static final List<String> GUARDED_PATTERNS = List.of(
            "/api/v1/orders",
            "/api/v1/payments",
            "/api/v1/refunds",
            "/api/v1/auth/register",
            "/api/v1/auth/seller/register");

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return GUARDED_PATTERNS.stream().noneMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            writeError(response, new ApiException(ErrorCode.VALIDATION_FAILED,
                    "The %s header is required on this endpoint".formatted(HEADER)));
            return;
        }

        // Buffer once, then pass a re-readable wrapper downstream. Consuming the
        // raw InputStream and forwarding ContentCachingRequestWrapper left the
        // controller with an empty body → MALFORMED_REQUEST on /auth/register.
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request, bodyBytes);
        String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);

        Optional<IdempotencyService.CachedResponse> replay;
        try {
            replay = idempotencyService.claimOrReplay(key, currentUserId(), request.getRequestURI(), requestBody);
        } catch (ApiException e) {
            writeError(response, e);
            return;
        }

        if (replay.isPresent()) {
            log.debug("Replaying cached response for idempotency key {}", key);
            response.setStatus(replay.get().status());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(replay.get().body() == null ? "{}" : replay.get().body());
            return;
        }

        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        boolean completedSuccessfully = false;
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
            int status = cachedResponse.getStatus();
            // Cache only successful responses. Client/parse errors must not poison
            // the key — otherwise retries keep replaying MALFORMED_REQUEST forever.
            if (status >= 200 && status < 300) {
                String body = new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                idempotencyService.completeRequest(key, status, body);
                completedSuccessfully = true;
            }
        } finally {
            if (!completedSuccessfully) {
                // Free the key so the client's retry is not permanently blocked.
                idempotencyService.releaseAbandonedRequest(key);
            }
            cachedResponse.copyBodyToResponse();
        }
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private void writeError(HttpServletResponse response, ApiException e) throws IOException {
        response.setStatus(e.getErrorCode().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(e.getErrorCode(), e.getMessage(), MDC.get("traceId"), List.of()));
    }
}
