package ch.bonflow.routing;

/** Published when a ticket was bumped or recalled at a station. */
public record TicketStatusChanged(long ticketId, long stationId, TicketStatus status) {
}
