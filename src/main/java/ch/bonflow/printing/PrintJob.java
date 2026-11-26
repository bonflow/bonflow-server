package ch.bonflow.printing;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One rendered ticket for one printer. The row is the outbox entry that survives printer outages. */
@Entity
@Table(name = "print_job")
class PrintJob {

    private static final long MAX_BACKOFF_SECONDS = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "printer_id", nullable = false)
    private Long printerId;

    @Column(nullable = false)
    private byte[] payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrintJobStatus status = PrintJobStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PrintJob() {
    }

    PrintJob(long ticketId, long printerId, byte[] payload, Instant now) {
        this.ticketId = ticketId;
        this.printerId = printerId;
        this.payload = payload;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void markSent(Instant now) {
        status = PrintJobStatus.SENT;
        lastError = null;
        updatedAt = now;
    }

    /** Keeps the job PENDING and retries with exponential backoff, capped at one minute. */
    void markAttemptFailed(String error, Instant now) {
        attempts++;
        lastError = error;
        nextAttemptAt = now.plus(Duration.ofSeconds(Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(attempts, 6))));
        updatedAt = now;
    }

    /** Makes a pending job due immediately, e.g. after the printer was fixed. */
    void retryNow(Instant now) {
        nextAttemptAt = now;
        updatedAt = now;
    }

    Long getId() {
        return id;
    }

    Long getTicketId() {
        return ticketId;
    }

    Long getPrinterId() {
        return printerId;
    }

    byte[] getPayload() {
        return payload;
    }

    PrintJobStatus getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }

    String getLastError() {
        return lastError;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getNextAttemptAt() {
        return nextAttemptAt;
    }
}
