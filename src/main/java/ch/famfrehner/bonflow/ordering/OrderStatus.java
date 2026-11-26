package ch.famfrehner.bonflow.ordering;

/** DRAFT is reserved for drafts kept on the server; the app currently only sends SUBMITTED orders. */
public enum OrderStatus {
    DRAFT, SUBMITTED, COMPLETED, CANCELLED
}
