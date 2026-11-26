package ch.famfrehner.bonflow.ordering;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.famfrehner.bonflow.masterdata.Article;
import ch.famfrehner.bonflow.masterdata.ArticleRepository;
import ch.famfrehner.bonflow.masterdata.DiningTable;
import ch.famfrehner.bonflow.masterdata.DiningTableRepository;
import ch.famfrehner.bonflow.masterdata.Modifier;
import ch.famfrehner.bonflow.masterdata.ModifierGroup;
import ch.famfrehner.bonflow.masterdata.Station;
import ch.famfrehner.bonflow.routing.TicketKind;
import ch.famfrehner.bonflow.routing.TicketService;
import ch.famfrehner.bonflow.routing.TicketService.RoutedItem;
import ch.famfrehner.bonflow.routing.TicketSummary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order lifecycle: {@code SUBMITTED} → {@code COMPLETED} or {@code CANCELLED}. Every submission
 * (initial, follow-up, storno) creates new station tickets in the same transaction and publishes
 * {@link OrderSubmitted}; tickets that were already handed to a station are never changed.
 */
@Service
public class OrderService {

    private final CustomerOrderRepository orders;
    private final DiningTableRepository tables;
    private final ArticleRepository articles;
    private final TicketService tickets;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OrderService(CustomerOrderRepository orders, DiningTableRepository tables, ArticleRepository articles,
            TicketService tickets, ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.tables = tables;
        this.articles = articles;
        this.tickets = tickets;
        this.events = events;
        this.clock = clock;
    }

    /** Idempotent: a known order id returns the stored order without creating tickets again. */
    @Transactional
    public OrderResult submit(SubmitOrder cmd, long waiterId) {
        var existing = orders.findById(cmd.id());
        if (existing.isPresent()) {
            if (!existing.get().getWaiterId().equals(waiterId)) {
                throw new OrderConflictException("Order id is already used by another waiter");
            }
            return new OrderResult(view(existing.get()), false);
        }
        DiningTable table = tables.findById(cmd.tableId()).filter(DiningTable::isActive)
                .orElseThrow(() -> new OrderRejectedException("Unknown or inactive table " + cmd.tableId()));

        CustomerOrder order = new CustomerOrder(cmd.id(), table.getId(), waiterId, clock.instant(), cmd.note());
        Map<OrderItem, Long> routed = buildItems(order, cmd.items(), 1);
        orders.saveAndFlush(order); // assigns item ids, needed for the tickets
        createTickets(order, 1, TicketKind.NEW, routed);
        return new OrderResult(view(order), true);
    }

    @Transactional
    public OrderView addItems(UUID orderId, AddItems cmd) {
        CustomerOrder order = requireOpen(orderId);
        int next = order.lastSubmissionNo() + 1;
        if (cmd.submissionNo() != next) {
            throw new OrderConflictException(
                    "Expected submission " + next + " but got " + cmd.submissionNo() + ", reload the order");
        }
        Map<OrderItem, Long> routed = buildItems(order, cmd.items(), next);
        orders.saveAndFlush(order);
        createTickets(order, next, TicketKind.ADDITION, routed);
        return view(order);
    }

    /** Cancels items; items that were already ticketed produce a STORNO ticket at their station. */
    @Transactional
    public OrderView cancelItems(UUID orderId, Set<Long> itemIds) {
        CustomerOrder order = requireOpen(orderId);
        if (itemIds == null || itemIds.isEmpty()) {
            throw new OrderRejectedException("No items to cancel");
        }
        Map<Long, OrderItem> byId = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, i -> i));
        List<OrderItem> toCancel = new ArrayList<>();
        for (Long id : itemIds) {
            OrderItem item = byId.get(id);
            if (item == null) {
                throw new OrderRejectedException("Item " + id + " does not belong to this order");
            }
            if (item.getStatus() == ItemStatus.CANCELLED) {
                throw new OrderRejectedException("Item " + id + " is already cancelled");
            }
            toCancel.add(item);
        }
        Map<Long, Long> stationOfItem = tickets.stationsOfItems(itemIds);
        List<RoutedItem> storno = toCancel.stream()
                .filter(i -> stationOfItem.containsKey(i.getId()))
                .map(i -> new RoutedItem(i.getId(), stationOfItem.get(i.getId())))
                .toList();

        toCancel.forEach(OrderItem::cancel);
        if (!order.hasActiveItems()) {
            order.cancel();
        }
        orders.saveAndFlush(order);
        if (!storno.isEmpty()) {
            int submissionNo = order.lastSubmissionNo();
            publish(order, submissionNo, TicketKind.STORNO,
                    tickets.createTickets(order.getId(), submissionNo, TicketKind.STORNO, storno));
        }
        return view(order);
    }

    /** The table is done (e.g. paid); no further items can be added. */
    @Transactional
    public OrderView complete(UUID orderId) {
        CustomerOrder order = requireOpen(orderId);
        order.complete();
        return view(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID orderId) {
        return view(orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId)));
    }

    @Transactional(readOnly = true)
    public List<OrderView> openOrdersOfTable(long tableId) {
        return orders.findAllByDiningTableIdAndStatusOrderByCreatedAt(tableId, OrderStatus.SUBMITTED).stream()
                .map(this::view)
                .toList();
    }

    private CustomerOrder requireOpen(UUID orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.SUBMITTED) {
            throw new OrderRejectedException("Order is " + order.getStatus() + " and can no longer be changed");
        }
        return order;
    }

    /** Validates the lines and attaches them to the order; returns each new item with its station. */
    private Map<OrderItem, Long> buildItems(CustomerOrder order, List<NewItem> lines, int submissionNo) {
        Map<Long, Article> byId = articles.findAllById(
                lines.stream().map(NewItem::articleId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Article::getId, a -> a));
        Map<OrderItem, Long> routed = new LinkedHashMap<>();
        for (NewItem line : lines) {
            Article article = byId.get(line.articleId());
            if (article == null || !article.isActive()) {
                throw new OrderRejectedException("Unknown or inactive article " + line.articleId());
            }
            Station station = article.resolveStation();
            if (station == null) {
                throw new OrderRejectedException("Article '" + article.getName() + "' has no station assigned");
            }
            Set<Long> modifierIds = line.modifierIds() == null ? Set.of() : line.modifierIds();
            validateModifiers(article, modifierIds);
            OrderItem item = new OrderItem(order, article.getId(), line.quantity(), blankToNull(line.note()),
                    line.course(), submissionNo, modifierIds);
            order.addItem(item);
            routed.put(item, station.getId());
        }
        return routed;
    }

    private static void validateModifiers(Article article, Set<Long> modifierIds) {
        Map<Long, ModifierGroup> groupOf = new HashMap<>();
        for (ModifierGroup group : article.getModifierGroups()) {
            for (Modifier modifier : group.getModifiers()) {
                groupOf.put(modifier.getId(), group);
            }
        }
        Map<Long, Integer> chosenPerGroup = new HashMap<>();
        for (Long modifierId : modifierIds) {
            ModifierGroup group = groupOf.get(modifierId);
            if (group == null) {
                throw new OrderRejectedException(
                        "Modifier " + modifierId + " is not available for '" + article.getName() + "'");
            }
            int chosen = chosenPerGroup.merge(group.getId(), 1, Integer::sum);
            if (chosen > 1 && !group.isMultiSelect()) {
                throw new OrderRejectedException(
                        "Only one choice allowed in '" + group.getName() + "' for '" + article.getName() + "'");
            }
        }
    }

    private void createTickets(CustomerOrder order, int submissionNo, TicketKind kind, Map<OrderItem, Long> routed) {
        List<RoutedItem> items = routed.entrySet().stream()
                .map(e -> new RoutedItem(e.getKey().getId(), e.getValue()))
                .toList();
        publish(order, submissionNo, kind, tickets.createTickets(order.getId(), submissionNo, kind, items));
    }

    private void publish(CustomerOrder order, int submissionNo, TicketKind kind, List<TicketSummary> created) {
        events.publishEvent(new OrderSubmitted(order.getId(), submissionNo, kind,
                created.stream().map(TicketSummary::id).toList()));
    }

    private OrderView view(CustomerOrder o) {
        return new OrderView(o.getId(), o.getDiningTableId(), o.getWaiterId(), o.getStatus(), o.getCreatedAt(),
                o.getSubmittedAt(), o.getNote(),
                o.getItems().stream().map(OrderView.ItemView::of).toList(),
                tickets.ticketsOfOrder(o.getId()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
