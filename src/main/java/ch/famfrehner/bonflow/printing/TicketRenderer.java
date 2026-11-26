package ch.famfrehner.bonflow.printing;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import ch.famfrehner.bonflow.printing.EscPosBuilder.Align;
import ch.famfrehner.bonflow.routing.TicketKind;

/** Lays out a {@link TicketContent} as ESC/POS bytes for a roll printer. */
final class TicketRenderer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private TicketRenderer() {
    }

    /** @param columns characters per line at normal size (48 for 80 mm, 32 for 58 mm paper) */
    static byte[] render(TicketContent ticket, int columns, ZoneId zone) {
        EscPosBuilder p = new EscPosBuilder().init();
        String divider = "-".repeat(columns);

        p.align(Align.CENTER).bold(true).size(2, 2).line(title(ticket.kind()));
        p.size(1, 1).line(ticket.stationName()).bold(false);
        p.align(Align.LEFT).line(divider);

        p.bold(true).size(2, 2).line("Tisch " + ticket.tableLabel()).size(1, 1).bold(false);
        String time = TIME.format(ticket.createdAt().atZone(zone));
        p.line(twoColumns("Bedienung: " + ticket.waiterName(), time, columns));
        if (ticket.orderNote() != null) {
            wrap("Notiz: ", ticket.orderNote(), columns).forEach(p::line);
        }
        p.line(divider);

        boolean hasCourses = ticket.lines().stream().anyMatch(l -> l.course() != null);
        List<TicketContent.Line> sorted = ticket.lines().stream()
                .sorted(Comparator.comparing(l -> l.course() == null ? 0 : l.course()))
                .toList();
        Integer currentCourse = null;
        boolean first = true;
        for (TicketContent.Line line : sorted) {
            if (hasCourses && (first || !Objects.equals(currentCourse, line.course()))) {
                p.bold(true).line(line.course() == null ? "-- ohne Gang --" : "-- Gang " + line.course() + " --")
                        .bold(false);
                currentCourse = line.course();
            }
            first = false;
            p.bold(true).size(1, 2);
            wrap(line.quantity() + "x ", line.name(), columns).forEach(p::line);
            p.size(1, 1).bold(false);
            line.modifiers().forEach(m -> wrap("  + ", m, columns).forEach(p::line));
            if (line.note() != null) {
                wrap("  > ", line.note(), columns).forEach(p::line);
            }
        }
        p.line(divider).feed(3).cut();
        return p.build();
    }

    private static String title(TicketKind kind) {
        return switch (kind) {
            case NEW -> "BESTELLUNG";
            case ADDITION -> "NACHBESTELLUNG";
            case STORNO -> "*** STORNO ***";
        };
    }

    private static String twoColumns(String left, String right, int columns) {
        int gap = Math.max(1, columns - left.length() - right.length());
        return left + " ".repeat(gap) + right;
    }

    /**
     * Word wrap to {@code columns}. The first line starts with {@code lead}, continuation lines are
     * indented by the same width. Words longer than a line are split.
     */
    static List<String> wrap(String lead, String text, int columns) {
        String indent = " ".repeat(lead.length());
        int avail = Math.max(1, columns - lead.length());
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : text.trim().split("\\s+")) {
            while (true) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (candidate.length() <= avail) {
                    current = candidate;
                    break;
                }
                if (!current.isEmpty()) {
                    lines.add((lines.isEmpty() ? lead : indent) + current);
                    current = "";
                    continue;
                }
                lines.add((lines.isEmpty() ? lead : indent) + word.substring(0, avail));
                word = word.substring(avail);
            }
        }
        lines.add((lines.isEmpty() ? lead : indent) + current);
        return lines;
    }
}
