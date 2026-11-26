package ch.bonflow.display;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ch.bonflow.ordering.OrderStatus;
import ch.bonflow.routing.TicketKind;
import ch.bonflow.routing.TicketStatus;

/** An order with names resolved and the delivery state of each ticket, for the admin history. */
public record OrderDetail(UUID id, String tableLabel, String waiterName, OrderStatus status, Instant createdAt,
        String note, List<Item> items, List<Ticket> tickets) {

    public record Item(int quantity, String name, Integer course, List<String> modifiers, String note,
            int submissionNo, boolean cancelled) {
    }

    /** @param printProblem delivery was attempted but failed so far */
    public record Ticket(long ticketId, String stationName, TicketKind kind, TicketStatus status, int submissionNo,
            Instant createdAt, Instant printedAt, Instant doneAt, boolean printProblem) {
    }
}
