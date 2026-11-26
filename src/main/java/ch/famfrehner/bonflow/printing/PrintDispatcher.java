package ch.famfrehner.bonflow.printing;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends due jobs to the printers. A job that cannot be delivered stays PENDING and is retried
 * with backoff, so nothing is lost while a printer is off or out of paper.
 */
@Component
class PrintDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PrintDispatcher.class);

    private final PrintJobService jobs;
    private final PrinterGateway gateway;

    PrintDispatcher(PrintJobService jobs, PrinterGateway gateway) {
        this.jobs = jobs;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${bonflow.printing.poll-interval:2s}")
    void dispatchDue() {
        Set<Long> unreachable = new HashSet<>();
        for (PrintJobService.DueJob job : jobs.dueJobs()) {
            if (unreachable.contains(job.printerId())) {
                continue; // keep the order of tickets per printer and do not hammer an offline printer
            }
            try {
                gateway.send(job.host(), job.port(), job.payload());
                jobs.markSent(job.id());
            } catch (IOException e) {
                unreachable.add(job.printerId());
                log.warn("Printer {}:{} not reachable (job {}): {}", job.host(), job.port(), job.id(),
                        e.toString());
                jobs.markAttemptFailed(job.id(), e.toString());
            }
        }
    }
}
