package ch.bonflow.masterdata;

import java.util.regex.Pattern;

/** Colours are stored as upper case {@code #RRGGBB} or not at all. */
public final class ColorCodes {

    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    private ColorCodes() {
    }

    /** @return the normalised colour, or {@code null} for null/blank input
     *  @throws IllegalArgumentException if the value is not of the form #RRGGBB */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!HEX.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Ungültige Farbe '" + value + "', erwartet #RRGGBB.");
        }
        return trimmed.toUpperCase(java.util.Locale.ROOT);
    }
}
