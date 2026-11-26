package ch.famfrehner.bonflow.ordering;

/** The request is well-formed but violates a business rule (inactive article, wrong state, ...). */
public class OrderRejectedException extends RuntimeException {

    public OrderRejectedException(String message) {
        super(message);
    }
}
