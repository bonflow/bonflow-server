package ch.bonflow.ordering;

/** The order changed in the meantime or belongs to someone else; the client should reload it. */
public class OrderConflictException extends RuntimeException {

    public OrderConflictException(String message) {
        super(message);
    }
}
