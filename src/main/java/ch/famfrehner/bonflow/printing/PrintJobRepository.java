package ch.famfrehner.bonflow.printing;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    boolean existsByTicketId(Long ticketId);

    @Query("""
            select j from PrintJob j
            where j.status = ch.famfrehner.bonflow.printing.PrintJobStatus.PENDING and j.nextAttemptAt <= :now
            order by j.id
            """)
    List<PrintJob> findDue(@Param("now") Instant now, Pageable page);
}
