package com.nirvaankar.marketplace.common.pagination;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Encodes a {@link Cursor} as base64("epochMicros|id").
 * <p>
 * LIMIT/OFFSET is banned for anything a mobile client scrolls: rows inserted
 * mid-scroll shift the window and the user sees duplicates or gaps. Keyset
 * pagination is stable because the position is a value, not a row count.
 */
public final class CursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String SEPARATOR = "|";

    private CursorCodec() {
    }

    public static String encode(Instant timestamp, long id) {
        long micros = timestamp.getEpochSecond() * 1_000_000L + timestamp.getNano() / 1_000L;
        String raw = micros + SEPARATOR + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        try {
            String raw = new String(DECODER.decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + SEPARATOR);
            if (parts.length != 2) {
                throw new IllegalArgumentException("expected 2 parts, got " + parts.length);
            }
            long micros = Long.parseLong(parts[0]);
            Instant timestamp = Instant.ofEpochSecond(micros / 1_000_000L, (micros % 1_000_000L) * 1_000L);
            return new Cursor(timestamp, Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_CURSOR);
        }
    }
}
