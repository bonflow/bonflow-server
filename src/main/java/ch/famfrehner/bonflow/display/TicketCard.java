package ch.famfrehner.bonflow.display;

import java.time.Instant;
import java.util.List;

import ch.famfrehner.bonflow.routing.TicketKind;
import ch.famfrehner.bonflow.routing.TicketStatus;

/** What a station display shows for one ticket. */
public record TicketCard(long ticketId, TicketKind kind, TicketStatus status, int submissionNo, String tableLabel,
        String waiterName, Instant createdAt, String orderNote, boolean printProblem, List<Line> lines) {

    /** @param cancelled the item was cancelled after this ticket was issued (a STORNO ticket follows) */
    public record Line(int quantity, String name, Integer course, List<String> modifiers, String note,
            boolean cancelled) {
    }
}
