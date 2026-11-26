package ch.bonflow.printing;

import ch.bonflow.ordering.OrderSubmitted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Runs after the order transaction committed. The event publication registry (transactional
 * outbox) re-delivers it after a crash, {@link PrintJobService#createJobs} is idempotent.
 */
@Component
class OrderSubmittedListener {

    private final PrintJobService jobs;

    OrderSubmittedListener(PrintJobService jobs) {
        this.jobs = jobs;
    }

    @ApplicationModuleListener
    void on(OrderSubmitted event) {
        jobs.createJobs(event.orderId(), event.ticketIds());
    }
}
