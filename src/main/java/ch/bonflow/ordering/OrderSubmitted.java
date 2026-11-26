package ch.bonflow.ordering;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import ch.bonflow.routing.TicketKind;

/**
 * Published in the same transaction that created the tickets (initial submission, follow-up or
 * storno, see {@code kind}). Printing and the station displays react to it.
 */
public record OrderSubmitted(UUID orderId, int submissionNo, TicketKind kind, List<Long> ticketIds,
        Set<Long> stationIds) {
}
