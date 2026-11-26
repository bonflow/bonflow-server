package ch.famfrehner.bonflow.routing;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** The part of an order one station has to prepare. References orders and items by id only. */
@Entity
@Table(name = "station_ticket")
class StationTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "submission_no", nullable = false)
    private int submissionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.NEW;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "printed_at")
    private Instant printedAt;

    @Column(name = "done_at")
    private Instant doneAt;

    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "station_ticket_item", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "order_item_id")
    private Set<Long> itemIds = new LinkedHashSet<>();

    protected StationTicket() {
    }

    StationTicket(UUID orderId, long stationId, int submissionNo, TicketKind kind, Instant createdAt,
            Set<Long> itemIds) {
        this.orderId = orderId;
        this.stationId = stationId;
        this.submissionNo = submissionNo;
        this.kind = kind;
        this.createdAt = createdAt;
        this.itemIds = new LinkedHashSet<>(itemIds);
    }

    /** NEW → IN_PROGRESS → DONE. */
    void advance(Instant now) {
        status = switch (status) {
            case NEW -> TicketStatus.IN_PROGRESS;
            case IN_PROGRESS -> TicketStatus.DONE;
            case DONE -> throw new IllegalStateException("Ticket " + id + " is already done");
        };
        if (status == TicketStatus.DONE) {
            doneAt = now;
        }
    }

    /** DONE → IN_PROGRESS ("recall" of a bumped ticket). */
    void recall() {
        if (status != TicketStatus.DONE) {
            throw new IllegalStateException("Only done tickets can be recalled, ticket " + id + " is " + status);
        }
        status = TicketStatus.IN_PROGRESS;
        doneAt = null;
    }

    void markPrinted(Instant when) {
        this.printedAt = when;
    }

    Long getId() {
        return id;
    }

    UUID getOrderId() {
        return orderId;
    }

    Long getStationId() {
        return stationId;
    }

    int getSubmissionNo() {
        return submissionNo;
    }

    TicketKind getKind() {
        return kind;
    }

    TicketStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getPrintedAt() {
        return printedAt;
    }

    Instant getDoneAt() {
        return doneAt;
    }

    Set<Long> getItemIds() {
        return itemIds;
    }
}
