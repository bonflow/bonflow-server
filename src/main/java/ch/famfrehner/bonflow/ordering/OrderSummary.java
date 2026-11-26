package ch.famfrehner.bonflow.ordering;

import java.time.Instant;
import java.util.UUID;

/** One row of the order history. */
public record OrderSummary(UUID id, long tableId, String tableLabel, long waiterId, String waiterName,
        OrderStatus status, Instant createdAt, int activeItems, int cancelledItems, String note) {
}
