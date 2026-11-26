package ch.bonflow.printing;

import java.time.Instant;
import java.util.List;

import ch.bonflow.routing.TicketKind;

/** Everything printed on one station ticket, resolved to display names. */
record TicketContent(TicketKind kind, String stationName, String tableLabel, String waiterName, Instant createdAt,
        String orderNote, boolean reprint, List<Line> lines) {

    record Line(int quantity, String name, Integer course, List<String> modifiers, String note) {
    }
}
