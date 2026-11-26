package ch.bonflow.printing;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    boolean existsByTicketId(Long ticketId);

    List<PrintJob> findAllByStatus(PrintJobStatus status);

    @Query("""
            select j from PrintJob j
            where j.status = ch.bonflow.printing.PrintJobStatus.PENDING and j.nextAttemptAt <= :now
            order by j.id
            """)
    List<PrintJob> findDue(@Param("now") Instant now, Pageable page);

    @Query("""
            select distinct j.ticketId from PrintJob j
            where j.ticketId in :ticketIds
              and j.status = ch.bonflow.printing.PrintJobStatus.PENDING and j.attempts > 0
            """)
    List<Long> findTicketsWithFailedAttempts(@Param("ticketIds") Collection<Long> ticketIds);
}
