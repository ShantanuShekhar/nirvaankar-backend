package com.nirvaankar.marketplace.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * SHA-256 helpers for values that must be verifiable but never readable:
 * refresh tokens, OTP codes, idempotent request bodies.
 * <p>
 * Deliberately NOT used for passwords - those go through BCrypt.
 */
public final class Hashing {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Hashing() {
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    /** Constant-time comparison so a hash check cannot be timing-attacked. */
    public static boolean matchesSha256(String raw, String expectedHex) {
        return MessageDigest.isEqual(
                sha256Hex(raw).getBytes(StandardCharsets.UTF_8),
                expectedHex.getBytes(StandardCharsets.UTF_8));
    }

    /** 256 bits of entropy, URL-safe. Used for refresh tokens. */
    public static String randomOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    public static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** Constant-time HMAC compare. Blank secret or signature never matches. */
    public static boolean hmacSha256Matches(String data, String secret, String expectedHex) {
        if (secret == null || secret.isBlank() || expectedHex == null || expectedHex.isBlank() || data == null) {
            return false;
        }
        byte[] actual = hmacSha256Hex(data, secret).getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return actual.length == expected.length && MessageDigest.isEqual(actual, expected);
    }

    /** Numeric OTP of the given length, uniformly distributed. */
    public static String randomNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
