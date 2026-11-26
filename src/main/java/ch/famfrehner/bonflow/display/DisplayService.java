package ch.famfrehner.bonflow.display;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.famfrehner.bonflow.masterdata.Article;
import ch.famfrehner.bonflow.masterdata.ArticleRepository;
import ch.famfrehner.bonflow.masterdata.DiningTable;
import ch.famfrehner.bonflow.masterdata.DiningTableRepository;
import ch.famfrehner.bonflow.masterdata.Modifier;
import ch.famfrehner.bonflow.masterdata.ModifierGroup;
import ch.famfrehner.bonflow.masterdata.ModifierGroupRepository;
import ch.famfrehner.bonflow.masterdata.Station;
import ch.famfrehner.bonflow.masterdata.StationRepository;
import ch.famfrehner.bonflow.ordering.ItemStatus;
import ch.famfrehner.bonflow.ordering.OrderService;
import ch.famfrehner.bonflow.ordering.OrderView;
import ch.famfrehner.bonflow.printing.PrintMonitor;
import ch.famfrehner.bonflow.routing.TicketService;
import ch.famfrehner.bonflow.routing.TicketSummary;
import ch.famfrehner.bonflow.users.WaiterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the board of a station from the database and executes bump / recall. */
@Service
public class DisplayService {

    static final int RECENTLY_DONE = 10;

    private final TicketService tickets;
    private final OrderService orders;
    private final StationRepository stations;
    private final ArticleRepository articles;
    private final ModifierGroupRepository modifierGroups;
    private final DiningTableRepository tables;
    private final WaiterService waiters;
    private final PrintMonitor printMonitor;

    DisplayService(TicketService tickets, OrderService orders, StationRepository stations,
            ArticleRepository articles, ModifierGroupRepository modifierGroups, DiningTableRepository tables,
            WaiterService waiters, PrintMonitor printMonitor) {
        this.tickets = tickets;
        this.orders = orders;
        this.stations = stations;
        this.articles = articles;
        this.modifierGroups = modifierGroups;
        this.tables = tables;
        this.waiters = waiters;
        this.printMonitor = printMonitor;
    }

    /** @throws NoSuchElementException for an unknown station */
    @Transactional(readOnly = true)
    public StationBoard board(long stationId) {
        Station station = stations.findById(stationId)
                .orElseThrow(() -> new NoSuchElementException("Unknown station " + stationId));
        List<TicketSummary> open = tickets.openTickets(stationId);
        List<TicketSummary> done = tickets.recentlyDone(stationId, RECENTLY_DONE);
        List<TicketSummary> all = Stream.concat(open.stream(), done.stream()).toList();

        Map<UUID, OrderView> orderById = new LinkedHashMap<>();
        for (TicketSummary t : all) {
            orderById.computeIfAbsent(t.orderId(), orders::get);
        }
        Set<Long> articleIds = orderById.values().stream().flatMap(o -> o.items().stream())
                .map(OrderView.ItemView::articleId).collect(Collectors.toSet());
        Map<Long, Article> articleById = articles.findAllById(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
        Map<Long, String> modifierName = new HashMap<>();
        for (ModifierGroup group : modifierGroups.findAll()) {
            for (Modifier m : group.getModifiers()) {
                modifierName.put(m.getId(), m.getName());
            }
        }
        Map<Long, String> tableLabel = tables.findAllById(
                orderById.values().stream().map(OrderView::tableId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(DiningTable::getId, DiningTable::getLabel));
        Map<Long, String> waiterName = new HashMap<>();
        orderById.values().stream().map(OrderView::waiterId).distinct()
                .forEach(id -> waiterName.put(id, waiters.displayName(id).orElse("?")));
        Set<Long> printProblems = printMonitor.ticketsWithPrintProblems(
                open.stream().map(TicketSummary::id).toList());

        List<TicketCard> openCards = new ArrayList<>();
        for (TicketSummary t : open) {
            openCards.add(card(t, orderById.get(t.orderId()), articleById, modifierName, tableLabel, waiterName,
                    printProblems.contains(t.id())));
        }
        List<TicketCard> doneCards = new ArrayList<>();
        for (TicketSummary t : done) {
            doneCards.add(card(t, orderById.get(t.orderId()), articleById, modifierName, tableLabel, waiterName,
                    false));
        }
        return new StationBoard(stationId, station.getName(), openCards, doneCards);
    }

    /** @throws ch.famfrehner.bonflow.ordering.OrderNotFoundException for an unknown order */
    @Transactional(readOnly = true)
    public OrderDetail orderDetail(UUID orderId) {
        OrderView order = orders.get(orderId);
        Map<Long, Article> articleById = articles.findAllById(
                order.items().stream().map(OrderView.ItemView::articleId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
        Map<Long, String> modifierName = new HashMap<>();
        for (ModifierGroup group : modifierGroups.findAll()) {
            for (Modifier m : group.getModifiers()) {
                modifierName.put(m.getId(), m.getName());
            }
        }
        Map<Long, String> stationName = stations.findAll().stream()
                .collect(Collectors.toMap(Station::getId, Station::getName));
        Set<Long> problems = printMonitor.ticketsWithPrintProblems(
                order.tickets().stream().map(TicketSummary::id).toList());

        List<OrderDetail.Item> items = order.items().stream().map(i -> {
            Article a = articleById.get(i.articleId());
            return new OrderDetail.Item(i.quantity(), a == null ? "Artikel " + i.articleId() : a.getName(),
                    i.course(), i.modifierIds().stream().sorted()
                            .map(id -> modifierName.getOrDefault(id, "?")).toList(),
                    i.note(), i.submissionNo(), i.status() == ItemStatus.CANCELLED);
        }).toList();
        List<OrderDetail.Ticket> ticketRows = order.tickets().stream()
                .map(t -> new OrderDetail.Ticket(t.id(), stationName.getOrDefault(t.stationId(), "?"), t.kind(),
                        t.status(), t.submissionNo(), t.createdAt(), t.printedAt(), t.doneAt(),
                        problems.contains(t.id())))
                .toList();
        return new OrderDetail(order.id(),
                tables.findById(order.tableId()).map(DiningTable::getLabel).orElse("?"),
                waiters.displayName(order.waiterId()).orElse("?"), order.status(), order.createdAt(), order.note(),
                items, ticketRows);
    }

    /** NEW → IN_PROGRESS → DONE. */
    public void advance(long ticketId) {
        tickets.advance(ticketId);
    }

    /** DONE → IN_PROGRESS. */
    public void recall(long ticketId) {
        tickets.recall(ticketId);
    }

    private static TicketCard card(TicketSummary t, OrderView order, Map<Long, Article> articleById,
            Map<Long, String> modifierName, Map<Long, String> tableLabel, Map<Long, String> waiterName,
            boolean printProblem) {
        List<TicketCard.Line> lines = order.items().stream()
                .filter(i -> t.itemIds().contains(i.id()))
                .map(i -> {
                    Article a = articleById.get(i.articleId());
                    String name = a == null ? "Artikel " + i.articleId()
                            : a.getShortName() != null && !a.getShortName().isBlank()
                                    ? a.getShortName() : a.getName();
                    return new TicketCard.Line(i.quantity(), name, i.course(),
                            i.modifierIds().stream().sorted().map(id -> modifierName.getOrDefault(id, "?")).toList(),
                            i.note(), i.status() == ItemStatus.CANCELLED);
                })
                .toList();
        return new TicketCard(t.id(), t.kind(), t.status(), t.submissionNo(),
                tableLabel.getOrDefault(order.tableId(), "?"), waiterName.getOrDefault(order.waiterId(), "?"),
                t.createdAt(), order.note(), printProblem, lines);
    }
}
