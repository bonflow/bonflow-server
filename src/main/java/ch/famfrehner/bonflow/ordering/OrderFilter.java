package ch.famfrehner.bonflow.ordering;

import java.time.Instant;

/** Optional criteria for the order history; {@code from} is inclusive, {@code to} exclusive. */
public record OrderFilter(Instant from, Instant to, Long tableId, Long waiterId, OrderStatus status) {

    public static OrderFilter none() {
        return new OrderFilter(null, null, null, null, null);
    }
}
