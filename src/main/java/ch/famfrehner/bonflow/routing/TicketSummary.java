package ch.famfrehner.bonflow.routing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model of a ticket; this is what other modules and the API see. */
public record TicketSummary(long id, UUID orderId, long stationId, int submissionNo, TicketKind kind, TicketStatus status,
        Instant createdAt, Instant printedAt, Instant doneAt, List<Long> itemIds) {

    static TicketSummary of(StationTicket t) {
        return new TicketSummary(t.getId(), t.getOrderId(), t.getStationId(), t.getSubmissionNo(), t.getKind(), t.getStatus(),
                t.getCreatedAt(), t.getPrintedAt(), t.getDoneAt(), t.getItemIds().stream().sorted().toList());
    }
}
