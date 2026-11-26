package ch.bonflow.ui.admin;

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;

/**
 * Colour picker for master data: a palette of large swatches (touch friendly, no free-form input
 * that could yield unreadable colours) plus "no colour". The value is {@code #RRGGBB} or null.
 */
class ColorField extends CustomField<String> {

    static final List<String> PALETTE = List.of(
            "#E53935", "#D81B60", "#8E24AA", "#5E35B1", "#3949AB", "#1E88E5",
            "#00ACC1", "#00897B", "#43A047", "#7CB342", "#FDD835", "#FB8C00",
            "#6D4C41", "#546E7A");

    private final Div swatches = new Div();
    private String value;

    ColorField(String label) {
        setLabel(label);
        swatches.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "0.4rem");
        add(swatches);
        render();
    }

    /** Coloured dot for grid columns; empty for no colour. */
    static Span dot(String color) {
        Span dot = new Span();
        if (color != null) {
            dot.getStyle().set("display", "inline-block").set("width", "1.25rem").set("height", "1.25rem")
                    .set("border-radius", "50%").set("background", color).set("vertical-align", "middle");
        }
        return dot;
    }

    private void render() {
        swatches.removeAll();
        Button none = new Button("Keine", e -> select(null));
        none.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        none.getStyle().set("font-weight", value == null ? "700" : "400");
        swatches.add(none);
        for (String color : PALETTE) {
            Button swatch = new Button();
            swatch.setAriaLabel("Farbe " + color);
            swatch.setWidth("2.5rem");
            swatch.setHeight("2.5rem");
            swatch.getStyle().set("background", color).set("border-radius", "50%").set("min-width", "2.5rem")
                    .set("padding", "0").set("cursor", "pointer")
                    .set("outline", color.equals(value) ? "3px solid currentColor" : "none")
                    .set("outline-offset", "2px");
            swatch.addClickListener(e -> select(color));
            swatches.add(swatch);
        }
    }

    private void select(String color) {
        setValue(color);
    }

    @Override
    protected String generateModelValue() {
        return value;
    }

    @Override
    protected void setPresentationValue(String newPresentationValue) {
        value = newPresentationValue;
        render();
    }
}
