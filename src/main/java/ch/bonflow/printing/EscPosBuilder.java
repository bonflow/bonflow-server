package ch.bonflow.printing;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Minimal ESC/POS command writer (Epson TM series and compatibles). Text is encoded as CP858,
 * which covers German umlauts, accents and the euro sign; unmappable characters become '?'.
 */
final class EscPosBuilder {

    enum Align {
        LEFT(0), CENTER(1), RIGHT(2);

        final int code;

        Align(int code) {
            this.code = code;
        }
    }

    private static final Charset CP858 = Charset.forName("Cp858");
    private static final int ESC = 0x1B;
    private static final int GS = 0x1D;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /** Reset the printer and select the CP858 code page. */
    EscPosBuilder init() {
        return raw(ESC, '@').raw(ESC, 't', 19);
    }

    EscPosBuilder align(Align align) {
        return raw(ESC, 'a', align.code);
    }

    EscPosBuilder bold(boolean on) {
        return raw(ESC, 'E', on ? 1 : 0);
    }

    /** Character size multiplier, 1 to 8 in each direction. */
    EscPosBuilder size(int width, int height) {
        return raw(GS, '!', ((width - 1) << 4) | (height - 1));
    }

    EscPosBuilder text(String text) {
        out.writeBytes(text.getBytes(CP858));
        return this;
    }

    EscPosBuilder line(String text) {
        return text(text).raw('\n');
    }

    EscPosBuilder feed(int lines) {
        return raw(ESC, 'd', lines);
    }

    /** Feed to the cutter and partially cut. */
    EscPosBuilder cut() {
        return raw(GS, 'V', 66, 3);
    }

    byte[] build() {
        return out.toByteArray();
    }

    private EscPosBuilder raw(int... bytes) {
        for (int b : bytes) {
            out.write(b);
        }
        return this;
    }
}
