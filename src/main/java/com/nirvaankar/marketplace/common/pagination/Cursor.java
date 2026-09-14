package com.nirvaankar.marketplace.common.pagination;

import java.time.Instant;

/**
 * Keyset pagination position: the (timestamp, id) pair of the last row the
 * client received. Opaque to the client - it round-trips the encoded string.
 */
public record Cursor(Instant timestamp, long id) {
}
