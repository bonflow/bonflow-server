package ch.bonflow.routing;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Splits submitted items into one ticket per station and runs the ticket state machine. */
@Service
public class TicketService {

    /** An order item together with the station that has to prepare it. */
    public record RoutedItem(long orderItemId, long stationId) {
    }

    private final TicketRepository tickets;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    TicketService(TicketRepository tickets, Clock clock, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.clock = clock;
        this.events = events;
    }

    /** One ticket per distinct station, in order of first appearance. */
    @Transactional
    public List<TicketSummary> createTickets(UUID orderId, int submissionNo, TicketKind kind,
            List<RoutedItem> items) {
        Map<Long, Set<Long>> byStation = new LinkedHashMap<>();
        for (RoutedItem item : items) {
            byStation.computeIfAbsent(item.stationId(), s -> new LinkedHashSet<>()).add(item.orderItemId());
        }
        return byStation.entrySet().stream()
                .map(e -> tickets.save(new StationTicket(orderId, e.getKey(), submissionNo, kind,
                        clock.instant(), e.getValue())))
                .map(TicketSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> ticketsOfOrder(UUID orderId) {
        return tickets.findAllByOrderIdOrderById(orderId).stream().map(TicketSummary::of).toList();
    }

    /** @return order item id → id of the station it was ticketed to (items never ticketed are absent) */
    @Transactional(readOnly = true)
    public Map<Long, Long> stationsOfItems(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return tickets.findStationsOfItems(itemIds).stream()
                .collect(Collectors.toMap(ItemStation::itemId, ItemStation::stationId, (a, b) -> a));
    }

    @Transactional
    public TicketSummary advance(long ticketId) {
        StationTicket ticket = require(ticketId);
        ticket.advance(clock.instant());
        return changed(ticket);
    }

    @Transactional
    public TicketSummary recall(long ticketId) {
        StationTicket ticket = require(ticketId);
        ticket.recall();
        return changed(ticket);
    }

    /** Tickets of a station that are not done yet, oldest first. */
    @Transactional(readOnly = true)
    public List<TicketSummary> openTickets(long stationId) {
        return tickets.findAllByStationIdAndStatusNotOrderById(stationId, TicketStatus.DONE).stream()
                .map(TicketSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TicketSummary> find(long ticketId) {
        return tickets.findById(ticketId).map(TicketSummary::of);
    }

    /** The most recently completed tickets of a station, by completion time, newest first (for "recall"). */
    @Transactional(readOnly = true)
    public List<TicketSummary> recentlyDone(long stationId, int limit) {
        return tickets.findByStationIdAndStatusOrderByDoneAtDescIdDesc(stationId, TicketStatus.DONE, PageRequest.of(0, limit))
                .stream().map(TicketSummary::of).toList();
    }

    @Transactional
    public void markPrinted(long ticketId) {
        require(ticketId).markPrinted(clock.instant());
    }

    private TicketSummary changed(StationTicket ticket) {
        events.publishEvent(new TicketStatusChanged(ticket.getId(), ticket.getStationId(), ticket.getStatus()));
        return TicketSummary.of(ticket);
    }

    private StationTicket require(long ticketId) {
        return tickets.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Unknown ticket " + ticketId));
    }
}
