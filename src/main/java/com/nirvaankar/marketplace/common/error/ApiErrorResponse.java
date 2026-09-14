package com.nirvaankar.marketplace.common.error;

import com.nirvaankar.marketplace.common.error.ApiException.FieldError;

import java.util.List;

/**
 * The one and only error shape this API returns. Identical for every failure
 * so a client can write a single error handler.
 * <pre>
 * { "error": { "code": "...", "message": "...", "traceId": "...", "fieldErrors": [...] } }
 * </pre>
 */
public record ApiErrorResponse(ErrorBody error) {

    public static ApiErrorResponse of(ErrorCode code, String message, String traceId, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(new ErrorBody(
                code.name(),
                message,
                traceId,
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors));
    }

    public record ErrorBody(String code, String message, String traceId, List<FieldError> fieldErrors) {
    }
}
