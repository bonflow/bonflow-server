package ch.bonflow.ui.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.bonflow.masterdata.Article;
import ch.bonflow.masterdata.ArticleRepository;
import ch.bonflow.masterdata.DiningTable;
import ch.bonflow.masterdata.DiningTableRepository;
import ch.bonflow.masterdata.Modifier;
import ch.bonflow.masterdata.ModifierGroup;
import ch.bonflow.ordering.NewItem;
import ch.bonflow.ordering.OrderConflictException;
import ch.bonflow.ordering.OrderRejectedException;
import ch.bonflow.ordering.OrderService;
import ch.bonflow.ordering.SubmitOrder;
import ch.bonflow.users.AppUser;
import ch.bonflow.users.AppUserRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Creates an order from the admin UI, on behalf of a waiter. The order goes through the same
 * {@link OrderService} as orders from the app, so tickets are created, printed and shown on the
 * station displays as usual.
 */
class NewOrderDialog extends Dialog {

    private record Line(Article article, int quantity, Integer course, Set<Modifier> modifiers, String note) {

        String description() {
            StringBuilder text = new StringBuilder(quantity + "× " + article.getName());
            if (!modifiers.isEmpty()) {
                text.append(" (").append(modifiers.stream().map(Modifier::getName).collect(Collectors.joining(", ")))
                        .append(')');
            }
            if (course != null) {
                text.append(" · Gang ").append(course);
            }
            if (note != null && !note.isBlank()) {
                text.append(" · ").append(note.trim());
            }
            return text.toString();
        }
    }

    private final OrderService orders;
    private final Runnable onCreated;

    private final ComboBox<DiningTable> table = new ComboBox<>("Tisch");
    private final ComboBox<AppUser> waiter = new ComboBox<>("Bedienung");
    private final TextField orderNote = new TextField("Notiz zur Bestellung");

    private final ComboBox<Article> article = new ComboBox<>("Artikel");
    private final IntegerField quantity = new IntegerField("Menge");
    private final ComboBox<Integer> course = new ComboBox<>("Gang");
    private final MultiSelectComboBox<Modifier> modifiers = new MultiSelectComboBox<>("Modifier");
    private final TextField lineNote = new TextField("Notiz");

    private final List<Line> lines = new ArrayList<>();
    private final Grid<Line> grid = new Grid<>();
    private final Button create = new Button("Bestellung anlegen", VaadinIcon.CHECK.create());

    NewOrderDialog(OrderService orders, DiningTableRepository tables, AppUserRepository users,
            ArticleRepository articles, Runnable onCreated) {
        this.orders = orders;
        this.onCreated = onCreated;
        setHeaderTitle("Neue Bestellung");
        setWidth("52rem");
        setMaxWidth("95vw");

        table.setItems(tables.findAll().stream().filter(DiningTable::isActive)
                .sorted(Comparator.comparing(DiningTable::getLabel)).toList());
        table.setItemLabelGenerator(DiningTable::getLabel);
        table.setRequiredIndicatorVisible(true);
        waiter.setItems(users.findAllByActiveTrueOrderByDisplayNameAsc());
        waiter.setItemLabelGenerator(AppUser::getDisplayName);
        waiter.setRequiredIndicatorVisible(true);
        waiter.setHelperText("Die Bestellung wird auf diese Person gebucht.");
        orderNote.setMaxLength(500);
        HorizontalLayout header = new HorizontalLayout(table, waiter, orderNote);
        header.setWidthFull();

        article.setItems(articles.findAll().stream().filter(Article::isActive)
                .sorted(Comparator.comparing(Article::getName)).toList());
        article.setItemLabelGenerator(Article::getName);
        article.addValueChangeListener(e -> offerModifiers(e.getValue()));
        article.setWidth("18rem");
        quantity.setValue(1);
        quantity.setMin(1);
        quantity.setMax(99);
        quantity.setStepButtonsVisible(true);
        quantity.setWidth("8rem");
        course.setItems(1, 2, 3, 4, 5, 6, 7, 8, 9);
        course.setClearButtonVisible(true);
        course.setWidth("7rem");
        modifiers.setItemLabelGenerator(Modifier::getName);
        modifiers.setEnabled(false);
        lineNote.setMaxLength(500);
        Button add = new Button("Hinzufügen", VaadinIcon.PLUS.create(), e -> addLine());
        HorizontalLayout lineEditor = new HorizontalLayout(article, quantity, course, modifiers, lineNote, add);
        lineEditor.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        lineEditor.setWidthFull();
        lineEditor.setFlexWrap(true);

        grid.addColumn(Line::description).setHeader("Position").setFlexGrow(1);
        grid.addComponentColumn(line -> {
            Button remove = new Button(VaadinIcon.TRASH.create(), e -> {
                lines.remove(line);
                refreshLines();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            remove.setAriaLabel("Position entfernen");
            return remove;
        }).setAutoWidth(true).setFlexGrow(0);
        grid.setAllRowsVisible(true);

        VerticalLayout content = new VerticalLayout(header, new H4("Positionen"), lineEditor, grid);
        content.setPadding(false);
        add(content);

        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(e -> submit());
        create.setEnabled(false);
        getFooter().add(new Button("Abbrechen", e -> close()), create);
    }

    private void offerModifiers(Article selected) {
        modifiers.clear();
        if (selected == null) {
            modifiers.setItems(List.of());
            modifiers.setEnabled(false);
            return;
        }
        List<Modifier> available = selected.getModifierGroups().stream()
                .sorted(Comparator.comparing(ModifierGroup::getName))
                .flatMap(g -> g.getModifiers().stream()).toList();
        modifiers.setItems(available);
        modifiers.setEnabled(!available.isEmpty());
    }

    private void addLine() {
        if (article.getValue() == null) {
            Notification.show("Bitte einen Artikel auswählen.");
            return;
        }
        Integer amount = quantity.getValue();
        if (amount == null || amount < 1 || amount > 99) {
            Notification.show("Die Menge muss zwischen 1 und 99 liegen.");
            return;
        }
        lines.add(new Line(article.getValue(), amount, course.getValue(), new LinkedHashSet<>(modifiers.getValue()),
                lineNote.getValue()));
        article.clear();
        quantity.setValue(1);
        course.clear();
        lineNote.clear();
        refreshLines();
    }

    private void refreshLines() {
        grid.setItems(List.copyOf(lines));
        create.setEnabled(!lines.isEmpty());
    }

    private void submit() {
        if (table.getValue() == null || waiter.getValue() == null) {
            Notification.show("Bitte Tisch und Bedienung auswählen.");
            return;
        }
        List<NewItem> items = lines.stream()
                .map(l -> new NewItem(l.article().getId(), l.quantity(),
                        l.note() == null || l.note().isBlank() ? null : l.note().trim(), l.course(),
                        l.modifiers().stream().map(Modifier::getId).collect(Collectors.toSet())))
                .toList();
        String note = orderNote.getValue() == null || orderNote.getValue().isBlank() ? null
                : orderNote.getValue().trim();
        try {
            orders.submit(new SubmitOrder(UUID.randomUUID(), table.getValue().getId(), note, items),
                    waiter.getValue().getId());
        } catch (OrderRejectedException | OrderConflictException e) {
            Notification.show("Bestellung nicht angelegt: " + e.getMessage(), 8000, Notification.Position.MIDDLE);
            return;
        }
        Notification.show("Bestellung für Tisch " + table.getValue().getLabel() + " angelegt.", 4000,
                Notification.Position.MIDDLE);
        onCreated.run();
        close();
    }
}
