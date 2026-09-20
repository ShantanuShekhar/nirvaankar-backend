package com.nirvaankar.marketplace.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes. The mobile and web clients switch on
 * {@code code}, never on the message text, so these strings are part of the
 * API contract - rename one and you break a shipped app.
 */
public enum ErrorCode {

    // 400 / 422 - request problems
    VALIDATION_FAILED("Request validation failed", HttpStatus.UNPROCESSABLE_ENTITY),
    MALFORMED_REQUEST("Request could not be parsed", HttpStatus.BAD_REQUEST),
    INVALID_CURSOR("Pagination cursor is not valid", HttpStatus.BAD_REQUEST),

    // 401 / 403 - auth
    INVALID_CREDENTIALS("Email, phone or password is incorrect", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("Access token has expired", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("Token is not valid", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED("Refresh token was already used; session revoked", HttpStatus.UNAUTHORIZED),
    ACCOUNT_INACTIVE("This account is suspended", HttpStatus.FORBIDDEN),
    FORBIDDEN("You do not have permission to do that", HttpStatus.FORBIDDEN),
    OTP_INVALID("The code is incorrect or has expired", HttpStatus.UNAUTHORIZED),
    OTP_ATTEMPTS_EXCEEDED("Too many incorrect attempts; request a new code", HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED("Please verify your email again before completing registration", HttpStatus.FORBIDDEN),
    RESET_TOKEN_INVALID("This reset link is invalid or has expired. Please request a new one.", HttpStatus.UNAUTHORIZED),
    EMAIL_SEND_FAILED("We could not send the email. Please try again shortly.", HttpStatus.SERVICE_UNAVAILABLE),

    // 404
    RESOURCE_NOT_FOUND("The requested resource does not exist", HttpStatus.NOT_FOUND),

    // 409 - business conflicts
    EMAIL_ALREADY_REGISTERED("An account with this email already exists", HttpStatus.CONFLICT),
    PHONE_ALREADY_REGISTERED("An account with this phone number already exists", HttpStatus.CONFLICT),
    SELLER_ALREADY_REGISTERED("This account is already registered as a seller", HttpStatus.CONFLICT),
    STORE_SLUG_TAKEN("A store with a similar name already exists; try a different store name", HttpStatus.CONFLICT),
    IDENTITY_ALREADY_LINKED("This login method is already linked to another account", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("Someone else changed this record; reload and retry", HttpStatus.CONFLICT),
    REQUEST_IN_PROGRESS("An identical request is already being processed", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED("This idempotency key was used with a different payload",
            HttpStatus.UNPROCESSABLE_ENTITY),
    PRODUCT_UNAVAILABLE("This product is not available to buy", HttpStatus.CONFLICT),
    INSUFFICIENT_INVENTORY("Not enough stock for this item", HttpStatus.CONFLICT),
    CART_EMPTY("Your bag is empty", HttpStatus.CONFLICT),
    BUY_NOW_EXPIRED("Your Buy Now session expired; pick the product again", HttpStatus.CONFLICT),
    PRICE_CHANGED("The price changed; review your bag and try again", HttpStatus.CONFLICT),
    INVALID_QUANTITY("Quantity must be at least 1", HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_IMAGE("That file is not an allowed product image", HttpStatus.UNPROCESSABLE_ENTITY),
    CHECKOUT_FAILED("Checkout could not be completed", HttpStatus.CONFLICT),
    PAYMENT_FAILED("Payment could not be completed", HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION("That status change is not allowed", HttpStatus.CONFLICT),

    // 429
    RATE_LIMITED("Too many requests; slow down", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RESEND_TOO_SOON("Please wait before requesting another code", HttpStatus.TOO_MANY_REQUESTS),

    // 503
    STORAGE_UNAVAILABLE("Image storage is not configured", HttpStatus.SERVICE_UNAVAILABLE),

    // 500
    INTERNAL_ERROR("Something went wrong on our side", HttpStatus.INTERNAL_SERVER_ERROR),
    UPLOAD_FAILED("The image could not be stored", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(String defaultMessage, HttpStatus status) {
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }
}
