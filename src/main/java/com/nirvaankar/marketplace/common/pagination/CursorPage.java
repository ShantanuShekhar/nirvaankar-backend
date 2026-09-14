package com.nirvaankar.marketplace.common.pagination;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * The response shape for every scrollable list endpoint.
 * There is deliberately no total count: computing one costs a second full
 * table scan on every page and no mobile UI actually needs it.
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }

    /**
     * Builds a page from a list fetched with {@code limit + 1} rows. The extra
     * row is the "is there more" probe and is trimmed before returning, which
     * avoids a separate COUNT query.
     */
    public static <T> CursorPage<T> from(List<T> fetchedWithProbe,
                                         int limit,
                                         Function<T, Instant> timestampExtractor,
                                         Function<T, Long> idExtractor) {
        boolean hasMore = fetchedWithProbe.size() > limit;
        List<T> items = hasMore ? fetchedWithProbe.subList(0, limit) : fetchedWithProbe;
        if (items.isEmpty()) {
            return empty();
        }
        T last = items.get(items.size() - 1);
        String nextCursor = hasMore
                ? CursorCodec.encode(timestampExtractor.apply(last), idExtractor.apply(last))
                : null;
        return new CursorPage<>(List.copyOf(items), nextCursor, hasMore);
    }

    public <R> CursorPage<R> map(Function<T, R> mapper) {
        return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor, hasMore);
    }
}
