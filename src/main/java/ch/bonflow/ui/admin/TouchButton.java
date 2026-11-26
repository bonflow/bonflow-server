package ch.bonflow.ui.admin;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Large, touch friendly tile button in the colour of an area, table or category. A plain
 * {@code Div} on purpose: its look does not depend on the component theme, and the text colour
 * is always chosen to be readable on the given background.
 */
final class TouchButton extends Div {

    TouchButton(String text, String subText, String color, boolean selected, Runnable onClick) {
        Span label = new Span((selected ? "✓ " : "") + text);
        label.getStyle().set("font-weight", "600").set("font-size", "1.15rem");
        add(label);
        if (subText != null && !subText.isBlank()) {
            Span sub = new Span(subText);
            sub.getStyle().set("font-size", "0.85rem").set("opacity", "0.85");
            add(sub);
        }
        String foreground = color == null ? "inherit" : textColor(color);
        getStyle().set("display", "flex").set("flex-direction", "column").set("align-items", "center")
                .set("justify-content", "center").set("text-align", "center").set("box-sizing", "border-box")
                .set("min-height", "4.25rem").set("padding", "0.5rem 0.75rem").set("border-radius", "0.75rem")
                .set("cursor", "pointer").set("user-select", "none").set("touch-action", "manipulation")
                .set("background", color == null ? "rgba(128, 128, 128, 0.22)" : color)
                .set("color", foreground).set("word-break", "break-word");
        if (selected) {
            getStyle().set("box-shadow", "inset 0 0 0 4px " + (color == null ? "currentColor" : foreground));
        }
        getElement().setAttribute("role", "button");
        getElement().setAttribute("tabindex", "0");
        addClickListener(e -> onClick.run());
    }

    /** Black or white, whichever is more readable on the given {@code #RRGGBB} background. */
    static String textColor(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.6 ? "#1B1B1B" : "#FFFFFF";
    }

    /** Wrapping container laying tiles out in a responsive grid. */
    static Div grid(String minWidth) {
        Div grid = new Div();
        grid.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(" + minWidth + ", 1fr))")
                .set("gap", "0.6rem").set("width", "100%");
        return grid;
    }
}
