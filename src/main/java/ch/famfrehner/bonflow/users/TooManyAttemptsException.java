package ch.famfrehner.bonflow.users;

import java.time.Duration;

/** Thrown when a waiter is temporarily locked after repeated wrong PINs. */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many failed attempts");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
