package ch.bonflow.printing;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to the delivery state of tickets, e.g. for a "printer offline" hint on displays. */
@Service
public class PrintMonitor {

    private final PrintJobRepository jobs;

    PrintMonitor(PrintJobRepository jobs) {
        this.jobs = jobs;
    }

    /** @return those tickets that could not be printed yet although delivery was attempted */
    @Transactional(readOnly = true)
    public Set<Long> ticketsWithPrintProblems(Collection<Long> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jobs.findTicketsWithFailedAttempts(ticketIds));
    }
}
