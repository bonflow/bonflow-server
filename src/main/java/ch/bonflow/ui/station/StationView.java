package ch.bonflow.ui.station;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import ch.bonflow.display.DisplayService;
import ch.bonflow.display.StationBoard;
import ch.bonflow.display.StationBroadcaster;
import ch.bonflow.display.TicketCard;
import ch.bonflow.routing.TicketKind;
import ch.bonflow.routing.TicketStatus;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
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
    private static final String GREEN = "#2e9e5b";
    private static final String AMBER = "#d98e04";
    private static final String RED = "#d93a3a";
    private static final String BLUE = "#2f6fed";
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
                .set("gap", "1rem").set("align-items", "start").set("width", "100%");
        emptyHint.setText("Keine offenen Bestellungen");
        emptyHint.getStyle().set("opacity", "0.6").set("font-size", "1.25rem");
        doneDetails.setWidthFull();
        doneList.getStyle().set("display", "grid").set("gap", "0.5rem");

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

    private Card cardView(TicketCard card, Instant now) {
        long minutes = Duration.between(card.createdAt(), now).toMinutes();
        String accent = accent(card, minutes);

        Card c = new Card();
        c.addThemeVariants(CardVariant.ELEVATED);
        c.getStyle().set("border-top", "0.5rem solid " + accent).set("min-width", "0");

        Span table = new Span("Tisch " + card.tableLabel());
        table.getStyle().set("font-size", "1.7rem").set("font-weight", "700");
        c.setTitle(table);

        Span meta = new Span(card.waiterName() + " · " + TIME.format(card.createdAt().atZone(ZoneId.systemDefault())));
        c.setSubtitle(meta);

        Span age = new Span(minutes + " min");
        age.getStyle().set("background", accent).set("color", "white").set("font-weight", "700")
                .set("font-size", "1.2rem").set("padding", "0.15rem 0.7rem").set("border-radius", "999px")
                .set("font-variant-numeric", "tabular-nums");
        c.setHeaderSuffix(age);

        Div body = new Div();
        body.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0.5rem");
        if (card.kind() != TicketKind.NEW || card.printProblem()) {
            Div badges = new Div();
            badges.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "0.4rem");
            if (card.kind() != TicketKind.NEW) {
                badges.add(badge(card.kind() == TicketKind.STORNO ? "STORNO" : "NACHBESTELLUNG",
                        card.kind() == TicketKind.STORNO ? RED : BLUE));
            }
            if (card.printProblem()) {
                badges.add(badge("⚠ Drucker nicht erreichbar", AMBER));
            }
            body.add(badges);
        }
        if (card.orderNote() != null) {
            Div note = new Div("Notiz: " + card.orderNote());
            note.getStyle().set("font-style", "italic");
            body.add(note);
        }

        Integer currentCourse = null;
        boolean first = true;
        boolean hasCourses = card.lines().stream().anyMatch(l -> l.course() != null);
        List<TicketCard.Line> lines = card.lines().stream()
                .sorted(Comparator.comparing(l -> l.course() == null ? 0 : l.course())).toList();
        for (TicketCard.Line line : lines) {
            if (hasCourses && (first || !Objects.equals(currentCourse, line.course()))) {
                Div course = new Div(line.course() == null ? "OHNE GANG" : "GANG " + line.course());
                course.getStyle().set("font-weight", "700").set("font-size", "0.85rem")
                        .set("letter-spacing", "0.08em").set("opacity", "0.65")
                        .set("border-bottom", "1px solid currentColor").set("padding-bottom", "0.15rem")
                        .set("margin-top", "0.4rem");
                body.add(course);
                currentCourse = line.course();
            }
            first = false;
            body.add(lineView(line, card.kind() != TicketKind.STORNO && line.cancelled()));
        }
        c.add(body);

        Button action = new Button(card.status() == TicketStatus.NEW ? "Starten" : "Fertig");
        action.setWidthFull();
        action.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_PRIMARY);
        if (card.status() == TicketStatus.IN_PROGRESS) {
            action.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        }
        action.addClickListener(e -> advance(card.ticketId()));
        c.addToFooter(action);
        return c;
    }

    private static Div lineView(TicketCard.Line line, boolean cancelled) {
        Div div = new Div();
        Span main = new Span(line.quantity() + "× " + line.name());
        main.getStyle().set("font-size", "1.3rem").set("font-weight", "600");
        if (cancelled) {
            main.getStyle().set("text-decoration", "line-through").set("opacity", "0.5");
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
        d.getStyle().set("margin-left", "1rem").set("font-size", "1.1rem").set("opacity", "0.85");
        return d;
    }

    private static Span badge(String text, String color) {
        Span span = new Span(text);
        span.getStyle().set("padding", "0.1rem 0.6rem").set("border", "2px solid " + color)
                .set("color", color).set("border-radius", "0.5rem").set("font-weight", "700")
                .set("font-size", "0.9rem");
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
            return BLUE;
        }
        if (minutes >= RED_AFTER_MINUTES) {
            return RED;
        }
        if (minutes >= AMBER_AFTER_MINUTES) {
            return AMBER;
        }
        return GREEN;
    }

    private static Long parse(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
