package ch.famfrehner.bonflow.ui.admin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import ch.famfrehner.bonflow.printing.PrintAdminService;
import ch.famfrehner.bonflow.printing.PrinterStatus;
import ch.famfrehner.bonflow.printing.TestPrintResult;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.RolesAllowed;

/** Which printers are reachable and how many tickets wait for them. */
@Route(value = "admin/printing", layout = AdminLayout.class)
@PageTitle("Druck-Status | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class PrintStatusView extends VerticalLayout {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");
    private static final int POLL_MILLIS = 10_000;

    private final PrintAdminService printAdmin;
    private final Grid<PrinterStatus> grid = new Grid<>();
    private Registration poll;

    public PrintStatusView(PrintAdminService printAdmin) {
        this.printAdmin = printAdmin;
        setSizeFull();

        grid.setSizeFull();
        grid.addComponentColumn(PrintStatusView::stateBadge).setHeader("Status").setAutoWidth(true);
        grid.addColumn(PrinterStatus::name).setHeader("Drucker");
        grid.addColumn(s -> s.host() + ":" + s.port()).setHeader("Adresse");
        grid.addColumn(PrinterStatus::pendingJobs).setHeader("Wartend");
        grid.addColumn(PrinterStatus::failingJobs).setHeader("Fehlgeschlagen");
        grid.addColumn(s -> s.waitingSince() == null ? ""
                : TIME.format(s.waitingSince().atZone(ZoneId.systemDefault()))).setHeader("Wartet seit");
        grid.addColumn(s -> s.lastError() == null ? "" : s.lastError()).setHeader("Letzter Fehler")
                .setFlexGrow(3);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true);

        Button reload = new Button("Aktualisieren", VaadinIcon.REFRESH.create(), e -> refresh());
        add(new H2("Druck-Status"), reload, grid);
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        event.getUI().setPollInterval(POLL_MILLIS);
        poll = event.getUI().addPollListener(e -> refresh());
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (poll != null) {
            poll.remove();
            poll = null;
        }
        event.getUI().setPollInterval(-1);
    }

    private void refresh() {
        grid.setItems(printAdmin.printerStatuses());
    }

    private HorizontalLayout actions(PrinterStatus status) {
        Button test = new Button("Testdruck", VaadinIcon.PRINT.create(), e -> {
            TestPrintResult result = printAdmin.testPrint(status.printerId());
            Notification n = Notification.show(status.name() + ": " + result.message(), 5000,
                    Notification.Position.MIDDLE);
            n.addThemeVariants(result.success() ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);
        });
        Button retry = new Button("Jetzt erneut senden", e -> {
            int jobs = printAdmin.retryNow(status.printerId());
            Notification.show(jobs + " Auftrag/Aufträge werden beim nächsten Durchlauf gesendet", 3000,
                    Notification.Position.MIDDLE);
            refresh();
        });
        retry.setEnabled(status.pendingJobs() > 0);
        test.addThemeVariants(ButtonVariant.LUMO_SMALL);
        retry.addThemeVariants(ButtonVariant.LUMO_SMALL);
        return new HorizontalLayout(test, retry);
    }

    private static Span stateBadge(PrinterStatus status) {
        Span span;
        if (!status.enabled()) {
            span = new Span("deaktiviert");
            span.getStyle().set("color", "var(--lumo-secondary-text-color)");
        } else if (status.offline()) {
            span = new Span("⚠ nicht erreichbar");
            span.getStyle().set("color", "var(--lumo-error-text-color)").set("font-weight", "700");
        } else if (status.pendingJobs() > 0) {
            span = new Span("sendet …");
            span.getStyle().set("color", "var(--lumo-warning-text-color)");
        } else {
            span = new Span("✓ bereit");
            span.getStyle().set("color", "var(--lumo-success-text-color)");
        }
        return span;
    }
}
