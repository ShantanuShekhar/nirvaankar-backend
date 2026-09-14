package com.nirvaankar.marketplace.common.error;

import lombok.Getter;

import java.util.List;

/**
 * The single exception type the API layer understands. Services throw this;
 * {@link GlobalExceptionHandler} turns it into the standard error envelope.
 * Nothing else should ever reach a client.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<FieldError> fieldErrors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of(), null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), null);
    }

    public ApiException(ErrorCode errorCode, String message, List<FieldError> fieldErrors, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, what + " not found");
    }

    public static ApiException conflict(ErrorCode code) {
        return new ApiException(code);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public record FieldError(String field, String message) {
    }
}
