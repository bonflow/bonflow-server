package ch.famfrehner.bonflow.printing;

import java.time.Instant;

/**
 * Delivery state of one printer.
 *
 * @param pendingJobs   tickets waiting to be printed
 * @param failingJobs   of those, jobs whose delivery was already attempted and failed
 * @param lastError     most recent delivery error, null when nothing failed
 * @param waitingSince  creation time of the oldest pending job
 */
public record PrinterStatus(long printerId, String name, String host, int port, boolean enabled, int pendingJobs,
        int failingJobs, String lastError, Instant waitingSince) {

    /** The printer cannot be reached although tickets are waiting for it. */
    public boolean offline() {
        return failingJobs > 0;
    }
}
