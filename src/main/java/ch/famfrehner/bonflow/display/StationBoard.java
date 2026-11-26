package ch.famfrehner.bonflow.display;

import java.util.List;

/** @param open tickets still to do, oldest first; @param recentlyDone last bumped tickets, newest first */
public record StationBoard(long stationId, String stationName, List<TicketCard> open,
        List<TicketCard> recentlyDone) {
}
