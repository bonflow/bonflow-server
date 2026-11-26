package ch.famfrehner.bonflow.ui.station;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import ch.famfrehner.bonflow.display.DisplayService;
import ch.famfrehner.bonflow.display.StationBoard;
import ch.famfrehner.bonflow.display.StationBroadcaster;
import ch.famfrehner.bonflow.display.TicketCard;
import ch.famfrehner.bonflow.routing.TicketKind;
import ch.famfrehner.bonflow.routing.TicketStatus;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.RolesAllowed;

/**
 * Station display, one browser (kiosk) per station at {@code /station/{stationId}}.
 *
 * <p>One card per ticket, oldest first. A tap on the card button advances NEW → IN_PROGRESS → DONE.
 * Changes arrive through {@link StationBroadcaster} (Vaadin push); the view additionally polls, so a
 * lost push or a reconnect never leaves it stale, and the elapsed times keep ticking.
 */
@Route("station/:stationId")
@PageTitle("Station | BonFlow")
@RolesAllowed({"STATION", "ADMIN", "MANAGER"})
public class StationView extends VerticalLayout implements BeforeEnterObserver {

    private static final int POLL_MILLIS = 15_000;
    private static final long AMBER_AFTER_MINUTES = 5;
    private static final long RED_AFTER_MINUTES = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final DisplayService display;
    private final StationBroadcaster broadcaster;
    private final H1 title = new H1("Station");
    private final Span clock = new Span();
    private final Div cards = new Div();
    private final Div emptyHint = new Div();
    private final Div doneList = new Div();
    private final Details doneDetails = new Details("Zuletzt erledigt", doneList);

    private Long stationId;
    private Runnable unregister;
    private Registration pollRegistration;

    public StationView(DisplayService display, StationBroadcaster broadcaster) {
        this.display = display;
        this.broadcaster = broadcaster;
        setSizeFull();
        setPadding(true);

        title.getStyle().set("margin", "0").set("font-size", "1.75rem");
        clock.getStyle().set("font-size", "1.5rem").set("font-variant-numeric", "tabular-nums");
        HorizontalLayout header = new HorizontalLayout(title, clock);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        cards.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(18rem, 1fr))")
                .set("gap", "var(--lumo-space-m)").set("align-items", "start").set("width", "100%");
        emptyHint.setText("Keine offenen Bestellungen");
        emptyHint.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "1.25rem");
        doneDetails.setWidthFull();
        doneList.getStyle().set("display", "grid").set("gap", "var(--lumo-space-s)");

        add(header, cards, emptyHint, doneDetails);
        setFlexGrow(1, cards);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        stationId = event.getRouteParameters().get("stationId").map(StationView::parse).orElse(null);
        if (stationId == null) {
            event.rerouteToError(NoSuchElementException.class, "Unbekannte Station");
            return;
        }
        try {
            title.setText(display.board(stationId).stationName());
        } catch (NoSuchElementException e) {
            event.rerouteToError(NoSuchElementException.class, "Unbekannte Station");
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        refresh();
        unregister = broadcaster.register(stationId, () -> ui.access(this::refresh));
        ui.setPollInterval(POLL_MILLIS);
        pollRegistration = ui.addPollListener(e -> refresh());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (unregister != null) {
            unregister.run();
            unregister = null;
        }
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        detachEvent.getUI().setPollInterval(-1);
    }

    private void refresh() {
        StationBoard board;
        try {
            board = display.board(stationId);
        } catch (RuntimeException e) {
            Notification.show("Verbindung zur Datenbank gestört, versuche es erneut …", 3000,
                    Notification.Position.BOTTOM_END);
            return;
        }
        Instant now = Instant.now();
        title.setText(board.stationName());
        clock.setText(TIME.format(now.atZone(ZoneId.systemDefault())));

        cards.removeAll();
        board.open().forEach(card -> cards.add(cardView(card, now)));
        emptyHint.setVisible(board.open().isEmpty());

        doneList.removeAll();
        board.recentlyDone().forEach(card -> doneList.add(doneRow(card)));
        doneDetails.setVisible(!board.recentlyDone().isEmpty());
    }

    private Div cardView(TicketCard card, Instant now) {
        long minutes = Duration.between(card.createdAt(), now).toMinutes();
        String accent = accent(card, minutes);

        Div div = new Div();
        div.getStyle().set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-left", "0.5rem solid " + accent)
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)");

        Span table = new Span("Tisch " + card.tableLabel());
        table.getStyle().set("font-size", "1.6rem").set("font-weight", "700");
        Span age = new Span(minutes + " min");
        age.getStyle().set("color", accent).set("font-weight", "700").set("font-size", "1.2rem");
        HorizontalLayout top = new HorizontalLayout(table, age);
        top.setWidthFull();
        top.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        top.setAlignItems(FlexComponent.Alignment.BASELINE);
        div.add(top);

        Span meta = new Span(card.waiterName() + " · " + TIME.format(card.createdAt().atZone(ZoneId.systemDefault())));
        meta.getStyle().set("color", "var(--lumo-secondary-text-color)");
        div.add(meta);

        if (card.kind() != TicketKind.NEW) {
            div.add(badge(card.kind() == TicketKind.STORNO ? "STORNO" : "NACHBESTELLUNG",
                    card.kind() == TicketKind.STORNO ? "var(--lumo-error-color)" : "var(--lumo-primary-color)"));
        }
        if (card.printProblem()) {
            div.add(badge("⚠ Drucker nicht erreichbar", "var(--lumo-warning-text-color)"));
        }
        if (card.orderNote() != null) {
            Div note = new Div("Notiz: " + card.orderNote());
            note.getStyle().set("font-style", "italic");
            div.add(note);
        }

        Div items = new Div();
        items.getStyle().set("margin", "var(--lumo-space-s) 0");
        Integer currentCourse = null;
        boolean first = true;
        boolean hasCourses = card.lines().stream().anyMatch(l -> l.course() != null);
        List<TicketCard.Line> lines = card.lines().stream()
                .sorted(Comparator.comparing(l -> l.course() == null ? 0 : l.course())).toList();
        for (TicketCard.Line line : lines) {
            if (hasCourses && (first || !Objects.equals(currentCourse, line.course()))) {
                Div course = new Div(line.course() == null ? "ohne Gang" : "Gang " + line.course());
                course.getStyle().set("font-weight", "700").set("margin-top", "var(--lumo-space-s)")
                        .set("color", "var(--lumo-secondary-text-color)");
                items.add(course);
                currentCourse = line.course();
            }
            first = false;
            items.add(lineView(line, card.kind() != TicketKind.STORNO && line.cancelled()));
        }
        div.add(items);

        Button action = new Button(card.status() == TicketStatus.NEW ? "Starten" : "Fertig");
        action.setWidthFull();
        action.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_PRIMARY);
        if (card.status() == TicketStatus.IN_PROGRESS) {
            action.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        }
        action.addClickListener(e -> advance(card.ticketId()));
        div.add(action);
        return div;
    }

    private static Div lineView(TicketCard.Line line, boolean cancelled) {
        Div div = new Div();
        Span main = new Span(line.quantity() + "× " + line.name());
        main.getStyle().set("font-size", "1.3rem").set("font-weight", "600");
        if (cancelled) {
            main.getStyle().set("text-decoration", "line-through").set("color", "var(--lumo-disabled-text-color)");
            div.add(main, new Span("  storniert"));
        } else {
            div.add(main);
        }
        line.modifiers().forEach(m -> div.add(subLine("+ " + m)));
        if (line.note() != null) {
            Div note = subLine("» " + line.note());
            note.getStyle().set("font-style", "italic");
            div.add(note);
        }
        return div;
    }

    private static Div subLine(String text) {
        Div d = new Div(text);
        d.getStyle().set("margin-left", "var(--lumo-space-m)").set("font-size", "1.1rem");
        return d;
    }

    private static Span badge(String text, String color) {
        Span span = new Span(text);
        span.getStyle().set("display", "inline-block").set("margin", "var(--lumo-space-xs) 0")
                .set("padding", "0 var(--lumo-space-s)").set("border", "2px solid " + color).set("color", color)
                .set("border-radius", "var(--lumo-border-radius-m)").set("font-weight", "700");
        return span;
    }

    private HorizontalLayout doneRow(TicketCard card) {
        Span text = new Span("Tisch " + card.tableLabel() + " · " + card.lines().stream()
                .map(l -> l.quantity() + "× " + l.name()).reduce((a, b) -> a + ", " + b).orElse(""));
        Button recall = new Button("Zurückholen", e -> recall(card.ticketId()));
        HorizontalLayout row = new HorizontalLayout(text, recall);
        row.setWidthFull();
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        return row;
    }

    private void advance(long ticketId) {
        run(() -> display.advance(ticketId));
    }

    private void recall(long ticketId) {
        run(() -> display.recall(ticketId));
    }

    /** Another display (or double tap) may have changed the ticket in the meantime: just reload. */
    private void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Notification.show("Ticket wurde inzwischen geändert", 2500, Notification.Position.BOTTOM_END);
        }
        refresh();
    }

    private static String accent(TicketCard card, long minutes) {
        if (card.status() == TicketStatus.IN_PROGRESS) {
            return "var(--lumo-primary-color)";
        }
        if (minutes >= RED_AFTER_MINUTES) {
            return "var(--lumo-error-color)";
        }
        if (minutes >= AMBER_AFTER_MINUTES) {
            return "var(--lumo-warning-color)";
        }
        return "var(--lumo-success-color)";
    }

    private static Long parse(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
