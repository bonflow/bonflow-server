package ch.famfrehner.bonflow.ordering;

/** @param created false when the order id already existed and the stored order is returned */
public record OrderResult(OrderView order, boolean created) {
}
