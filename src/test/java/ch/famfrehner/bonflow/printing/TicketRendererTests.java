package ch.famfrehner.bonflow.printing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import ch.famfrehner.bonflow.routing.TicketKind;
import org.junit.jupiter.api.Test;

class TicketRendererTests {

    private static final Charset CP858 = Charset.forName("Cp858");

    private static TicketContent content(TicketKind kind, TicketContent.Line... lines) {
        return new TicketContent(kind, "Küche", "T12", "Anna", Instant.parse("2026-11-26T17:34:00Z"),
                "Allergie: Nüsse", List.of(lines));
    }

    private static String text(byte[] bytes) {
        return new String(bytes, CP858);
    }

    @Test
    void rendersHeaderItemsModifiersAndNotes() {
        byte[] bytes = TicketRenderer.render(content(TicketKind.NEW,
                new TicketContent.Line(2, "Schnitzel", null, List.of("medium", "Käse"), "ohne Salz")),
                48, ZoneId.of("Europe/Zurich"));
        String out = text(bytes);

        assertThat(out).contains("BESTELLUNG", "Küche", "Tisch T12", "Bedienung: Anna", "18:34",
                "Notiz: Allergie: Nüsse", "2x Schnitzel", "  + medium", "  + Käse", "  > ohne Salz");
        // starts with ESC @ (init), ends with the partial cut GS V 66 3
        assertThat(bytes[0]).isEqualTo((byte) 0x1B);
        assertThat(bytes[1]).isEqualTo((byte) '@');
        assertThat(bytes[bytes.length - 4]).isEqualTo((byte) 0x1D);
        assertThat(bytes[bytes.length - 3]).isEqualTo((byte) 'V');
    }

    @Test
    void marksAdditionsAndStornos() {
        TicketContent.Line line = new TicketContent.Line(1, "Bier", null, List.of(), null);
        assertThat(text(TicketRenderer.render(content(TicketKind.ADDITION, line), 48, ZoneId.of("UTC"))))
                .contains("NACHBESTELLUNG");
        assertThat(text(TicketRenderer.render(content(TicketKind.STORNO, line), 48, ZoneId.of("UTC"))))
                .contains("*** STORNO ***");
    }

    @Test
    void groupsByCourseWhenCoursesAreUsed() {
        String out = text(TicketRenderer.render(content(TicketKind.NEW,
                new TicketContent.Line(1, "Steak", 2, List.of(), null),
                new TicketContent.Line(1, "Suppe", 1, List.of(), null)), 48, ZoneId.of("UTC")));
        assertThat(out.indexOf("Gang 1")).isPositive().isLessThan(out.indexOf("Suppe"));
        assertThat(out.indexOf("Suppe")).isLessThan(out.indexOf("Gang 2"));
        assertThat(out.indexOf("Gang 2")).isLessThan(out.indexOf("Steak"));
    }

    @Test
    void wrapsLongTextWithinColumns() {
        List<String> lines = TicketRenderer.wrap("1x ", "Rindsfilet mit Kräuterbutter und Röstkartoffeln", 20);
        assertThat(lines).allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(20));
        assertThat(lines.get(0)).startsWith("1x ");
        assertThat(lines.get(1)).startsWith("   ");
        assertThat(String.join(" ", lines).replaceAll("\\s+", " "))
                .contains("Rindsfilet mit Kräuterbutter und Röstkartoffeln".substring(0, 10));

        assertThat(TicketRenderer.wrap("", "Donaudampfschifffahrtsgesellschaft", 10))
                .allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(10));
    }
}
