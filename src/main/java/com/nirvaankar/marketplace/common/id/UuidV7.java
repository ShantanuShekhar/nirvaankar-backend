package com.nirvaankar.marketplace.common.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID v7 generator (RFC 9562): 48-bit Unix millisecond timestamp followed by
 * random bits. Time-ordered, so it does not fragment a B-tree index the way
 * v4 does, while still being unguessable enough to stop enumeration attacks.
 * <p>
 * Every externally visible identifier in this system is a v7 UUID stored as
 * BINARY(16). Internal joins use BIGINT ids that never leave the service layer.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return generateAt(System.currentTimeMillis());
    }

    static UUID generateAt(long unixMillis) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);

        bytes[0] = (byte) (unixMillis >>> 40);
        bytes[1] = (byte) (unixMillis >>> 32);
        bytes[2] = (byte) (unixMillis >>> 24);
        bytes[3] = (byte) (unixMillis >>> 16);
        bytes[4] = (byte) (unixMillis >>> 8);
        bytes[5] = (byte) unixMillis;

        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);   // version 7
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);   // IETF variant

        return toUuid(bytes);
    }

    public static byte[] toBytes(UUID uuid) {
        byte[] out = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (msb >>> (8 * (7 - i)));
            out[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
        }
        return out;
    }

    public static UUID toUuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("A UUID must be exactly 16 bytes");
        }
        long msb = 0L;
        long lsb = 0L;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFFL);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFFL);
        }
        return new UUID(msb, lsb);
    }
}
