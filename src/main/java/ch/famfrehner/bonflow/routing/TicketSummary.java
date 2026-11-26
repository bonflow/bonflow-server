package ch.famfrehner.bonflow.routing;

import java.time.Instant;
import java.util.List;

/** Read model of a ticket; this is what other modules and the API see. */
public record TicketSummary(long id, long stationId, int submissionNo, TicketKind kind, TicketStatus status,
        Instant createdAt, Instant printedAt, List<Long> itemIds) {

    static TicketSummary of(StationTicket t) {
        return new TicketSummary(t.getId(), t.getStationId(), t.getSubmissionNo(), t.getKind(), t.getStatus(),
                t.getCreatedAt(), t.getPrintedAt(), t.getItemIds().stream().sorted().toList());
    }
}
