package ch.bonflow.ui.admin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ch.bonflow.display.DisplayService;
import ch.bonflow.display.OrderDetail;
import ch.bonflow.masterdata.AreaRepository;
import ch.bonflow.masterdata.ArticleCategoryRepository;
import ch.bonflow.masterdata.ArticleRepository;
import ch.bonflow.masterdata.SettingsService;
import ch.bonflow.masterdata.DiningTable;
import ch.bonflow.masterdata.DiningTableRepository;
import ch.bonflow.ordering.OrderConflictException;
import ch.bonflow.ordering.OrderFilter;
import ch.bonflow.ordering.OrderRejectedException;
import ch.bonflow.ordering.OrderView;
import ch.bonflow.ordering.OrderService;
import ch.bonflow.ordering.OrderStatus;
import ch.bonflow.ordering.OrderSummary;
import ch.bonflow.printing.PrintAdminService;
import ch.bonflow.users.AppUser;
import ch.bonflow.users.AppUserRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/**
 * Order history with filters; tickets can be printed again from the detail dialog, and new orders can be
 * created on behalf of a waiter.
 */
@Route(value = "admin/orders", layout = AdminLayout.class)
@PageTitle("Bestellungen | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class OrdersView extends VerticalLayout {

    private static final int LIMIT = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DiningTableRepository tableRepository;
    private final AreaRepository areaRepository;
    private final AppUserRepository userRepository;
    private final ArticleCategoryRepository categoryRepository;
    private final ArticleRepository articleRepository;
    private final SettingsService settingsService;
    private final OrderService orders;
    private final DisplayService display;
    private final PrintAdminService printAdmin;
    private final Grid<OrderSummary> grid = new Grid<>();
    private final DatePicker from = new DatePicker("Von");
    private final DatePicker to = new DatePicker("Bis (inkl.)");
    private final ComboBox<DiningTable> table = new ComboBox<>("Tisch");
    private final ComboBox<AppUser> waiter = new ComboBox<>("Bedienung");
    private final ComboBox<OrderStatus> status = new ComboBox<>("Status");

    public OrdersView(OrderService orders, DisplayService display, PrintAdminService printAdmin,
            DiningTableRepository tables, AppUserRepository users, ArticleRepository articles,
            AreaRepository areas, ArticleCategoryRepository categories, SettingsService settings) {
        this.tableRepository = tables;
        this.areaRepository = areas;
        this.userRepository = users;
        this.categoryRepository = categories;
        this.articleRepository = articles;
        this.settingsService = settings;
        this.orders = orders;
        this.display = display;
        this.printAdmin = printAdmin;
        setSizeFull();

        from.setValue(LocalDate.now());
        table.setItems(tables.findAll().stream().sorted(java.util.Comparator.comparing(DiningTable::getLabel)).toList());
        table.setItemLabelGenerator(DiningTable::getLabel);
        table.setClearButtonVisible(true);
        waiter.setItems(users.findAllByOrderByDisplayNameAsc());
        waiter.setItemLabelGenerator(AppUser::getDisplayName);
        waiter.setClearButtonVisible(true);
        status.setItems(OrderStatus.values());
        status.setClearButtonVisible(true);
        Button search = new Button("Suchen", VaadinIcon.SEARCH.create(), e -> refresh());
        search.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout filters = new HorizontalLayout(from, to, table, waiter, status, search);
        filters.setDefaultVerticalComponentAlignment(Alignment.END);

        grid.setSizeFull();
        grid.addColumn(o -> TIME.format(o.createdAt().atZone(ZoneId.systemDefault()))).setHeader("Zeit");
        grid.addColumn(OrderSummary::tableLabel).setHeader("Tisch");
        grid.addColumn(OrderSummary::waiterName).setHeader("Bedienung");
        grid.addColumn(o -> label(o.status())).setHeader("Status");
        grid.addColumn(o -> o.activeItems() + (o.cancelledItems() > 0 ? " (+" + o.cancelledItems() + " storniert)" : ""))
                .setHeader("Positionen");
        grid.addColumn(o -> o.note() == null ? "" : o.note()).setHeader("Notiz").setFlexGrow(2);
        grid.addItemClickListener(e -> openDetail(e.getItem().id()));

        Button create = new Button("Neue Bestellung", VaadinIcon.PLUS.create(),
                e -> new NewOrderDialog(orders, tables, areas, users, categories, articles, settings.coursesEnabled(),
                        null, this::refresh).open());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(new H2("Bestellungen"), create, filters, grid,
                new Paragraph("Neueste zuerst, höchstens " + LIMIT + " Einträge."));
        refresh();
    }

    private void refresh() {
        var zone = ZoneId.systemDefault();
        OrderFilter filter = new OrderFilter(
                from.getValue() == null ? null : from.getValue().atStartOfDay(zone).toInstant(),
                to.getValue() == null ? null : to.getValue().plusDays(1).atStartOfDay(zone).toInstant(),
                table.getValue() == null ? null : table.getValue().getId(),
                waiter.getValue() == null ? null : waiter.getValue().getId(),
                status.getValue());
        grid.setItems(orders.search(filter, LIMIT));
    }

    private void openDetail(UUID orderId) {
        Dialog dialog = new Dialog();
        dialog.setWidth("60rem");
        dialog.setMaxWidth("95vw");
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            dialog.removeAll();
            OrderDetail detail = display.orderDetail(orderId);
            dialog.setHeaderTitle("Tisch " + detail.tableLabel() + " · "
                    + TIME.format(detail.createdAt().atZone(ZoneId.systemDefault())));

            Grid<OrderDetail.Item> items = new Grid<>();
            items.addColumn(i -> i.quantity() + "×").setHeader("Menge").setAutoWidth(true);
            items.addColumn(i -> i.cancelled() ? i.name() + " (storniert)" : i.name()).setHeader("Artikel")
                    .setFlexGrow(2);
            items.addColumn(i -> String.join(", ", i.modifiers())).setHeader("Modifier");
            items.addColumn(i -> i.note() == null ? "" : i.note()).setHeader("Notiz");
            items.addColumn(i -> i.course() == null ? "" : "Gang " + i.course()).setHeader("Gang");
            items.addColumn(OrderDetail.Item::submissionNo).setHeader("Nr.");
            boolean open = detail.status() == OrderStatus.SUBMITTED;
            if (open) {
                items.addComponentColumn(item -> {
                    if (item.cancelled()) {
                        return new Span();
                    }
                    Button cancel = new Button("Stornieren", VaadinIcon.CLOSE_SMALL.create(),
                            e -> confirmCancel(orderId, item, render[0]));
                    cancel.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                    return cancel;
                }).setHeader("").setAutoWidth(true).setFlexGrow(0);
            }
            items.setItems(detail.items());
            items.setAllRowsVisible(true);

            Grid<OrderDetail.Ticket> tickets = new Grid<>();
            tickets.addColumn(OrderDetail.Ticket::stationName).setHeader("Station");
            tickets.addColumn(t -> t.kind().name()).setHeader("Art");
            tickets.addColumn(t -> t.status().name()).setHeader("Status");
            tickets.addColumn(t -> SECONDS.format(t.createdAt().atZone(ZoneId.systemDefault()))).setHeader("Erstellt");
            tickets.addColumn(t -> t.printedAt() == null ? (t.printProblem() ? "⚠ Fehler" : "–")
                    : SECONDS.format(t.printedAt().atZone(ZoneId.systemDefault()))).setHeader("Gedruckt");
            tickets.addColumn(t -> t.doneAt() == null ? "–"
                    : SECONDS.format(t.doneAt().atZone(ZoneId.systemDefault()))).setHeader("Erledigt");
            tickets.addComponentColumn(t -> {
                Button reprint = new Button("Nachdruck", VaadinIcon.PRINT.create(), e -> {
                    int jobs = printAdmin.reprintTicket(t.ticketId());
                    Notification.show(jobs == 0 ? "Kein aktiver Drucker für diese Station"
                            : "Nachdruck an " + jobs + " Drucker gesendet", 3000, Notification.Position.MIDDLE);
                    render[0].run();
                });
                reprint.addThemeVariants(ButtonVariant.LUMO_SMALL);
                return reprint;
            }).setHeader("").setAutoWidth(true);
            tickets.setItems(detail.tickets());
            tickets.setAllRowsVisible(true);

            VerticalLayout content = new VerticalLayout(
                    new Paragraph("Bedienung: " + detail.waiterName() + " · Status: " + label(detail.status())
                            + (detail.note() == null ? "" : " · Notiz: " + detail.note())));
            if (open) {
                Button addItems = new Button("Positionen nachbestellen", VaadinIcon.PLUS.create(),
                        e -> openAddition(orderId, render[0]));
                addItems.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                content.add(addItems);
            }
            content.add(new H3("Positionen"), items, new H3("Tickets"), tickets);
            dialog.add(content);
        };
        render[0].run();
        dialog.getFooter().add(new Button("Schliessen", e -> dialog.close()));
        dialog.open();
    }

    private void confirmCancel(UUID orderId, OrderDetail.Item item, Runnable afterwards) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Position stornieren?");
        confirm.setText(item.quantity() + "× " + item.name() + " wird storniert. War sie schon an der Station "
                + "gedruckt, entsteht dort ein Storno-Bon.");
        confirm.setCancelable(true);
        confirm.setCancelText("Abbrechen");
        confirm.setConfirmText("Stornieren");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            try {
                orders.cancelItems(orderId, Set.of(item.id()));
            } catch (OrderRejectedException | OrderConflictException ex) {
                Notification.show("Nicht storniert: " + ex.getMessage(), 6000, Notification.Position.MIDDLE);
            }
            afterwards.run();
            refresh();
        });
        confirm.open();
    }

    private void openAddition(UUID orderId, Runnable afterwards) {
        OrderView order = orders.get(orderId);
        int nextSubmission = order.items().stream().mapToInt(OrderView.ItemView::submissionNo).max().orElse(0) + 1;
        DiningTable table = tableRepository.findById(order.tableId()).orElse(null);
        if (table == null) {
            Notification.show("Tisch nicht gefunden.", 4000, Notification.Position.MIDDLE);
            return;
        }
        new NewOrderDialog(orders, tableRepository, areaRepository, userRepository, categoryRepository,
                articleRepository, settingsService.coursesEnabled(), new NewOrderDialog.Addition(orderId, table,
                        nextSubmission), () -> {
                    afterwards.run();
                    refresh();
                }).open();
    }

    private static String label(OrderStatus status) {
        return switch (status) {
            case DRAFT -> "Entwurf";
            case SUBMITTED -> "Offen";
            case COMPLETED -> "Abgeschlossen";
            case CANCELLED -> "Storniert";
        };
    }
}
