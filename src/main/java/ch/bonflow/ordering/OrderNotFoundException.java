package ch.bonflow.ordering;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("Unknown order " + id);
    }
}
