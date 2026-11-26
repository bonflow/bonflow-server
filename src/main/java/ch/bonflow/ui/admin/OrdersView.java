package ch.bonflow.ui.admin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import ch.bonflow.display.DisplayService;
import ch.bonflow.display.OrderDetail;
import ch.bonflow.masterdata.DiningTable;
import ch.bonflow.masterdata.DiningTableRepository;
import ch.bonflow.ordering.OrderFilter;
import ch.bonflow.ordering.OrderService;
import ch.bonflow.ordering.OrderStatus;
import ch.bonflow.ordering.OrderSummary;
import ch.bonflow.printing.PrintAdminService;
import ch.bonflow.users.AppUser;
import ch.bonflow.users.AppUserRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/** Read-only order history with filters; tickets can be printed again from the detail dialog. */
@Route(value = "admin/orders", layout = AdminLayout.class)
@PageTitle("Bestellungen | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class OrdersView extends VerticalLayout {

    private static final int LIMIT = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");

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
            DiningTableRepository tables, AppUserRepository users) {
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

        add(new H2("Bestellungen"), filters, grid,
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

            dialog.add(new VerticalLayout(
                    new Paragraph("Bedienung: " + detail.waiterName() + " · Status: " + label(detail.status())
                            + (detail.note() == null ? "" : " · Notiz: " + detail.note())),
                    new H3("Positionen"), items, new H3("Tickets"), tickets));
        };
        render[0].run();
        dialog.getFooter().add(new Button("Schliessen", e -> dialog.close()));
        dialog.open();
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
