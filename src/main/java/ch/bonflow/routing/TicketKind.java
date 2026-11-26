package ch.bonflow.routing;

public enum TicketKind {
    /** First submission of an order. */
    NEW,
    /** Follow-up items for an order that was already submitted ("Nachbestellung"). */
    ADDITION,
    /** Cancellation of items that were already ticketed. */
    STORNO
}
