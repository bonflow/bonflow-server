package ch.bonflow.ui.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.bonflow.masterdata.Area;
import ch.bonflow.masterdata.AreaRepository;
import ch.bonflow.masterdata.Article;
import ch.bonflow.masterdata.ArticleCategory;
import ch.bonflow.masterdata.ArticleCategoryRepository;
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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Creates an order from the admin UI, on behalf of a waiter, with large touch friendly buttons:
 * step 1 picks the waiter, area and table, step 2 the category and the articles. The order goes
 * through the same {@link OrderService} as orders from the app, so tickets are created, printed and
 * shown on the station displays as usual.
 */
class NewOrderDialog extends Dialog {

    private static final int MAX_COURSE = 5;

    private static final class Line {
        final Article article;
        int quantity;
        final Integer course;
        final Set<Modifier> modifiers;
        String note;

        Line(Article article, int quantity, Integer course, Set<Modifier> modifiers, String note) {
            this.article = article;
            this.quantity = quantity;
            this.course = course;
            this.modifiers = modifiers;
            this.note = note;
        }

        boolean sameAs(Article otherArticle, Integer otherCourse, Set<Modifier> otherModifiers) {
            return article.equals(otherArticle) && Objects.equals(course, otherCourse)
                    && modifiers.equals(otherModifiers) && (note == null || note.isBlank());
        }

        String description() {
            StringBuilder text = new StringBuilder(article.getName());
            if (!modifiers.isEmpty()) {
                text.append(" (").append(modifiers.stream().map(Modifier::getName).collect(Collectors.joining(", ")))
                        .append(')');
            }
            if (course != null) {
                text.append(" · Gang ").append(course);
            }
            if (note != null && !note.isBlank()) {
                text.append(" · » ").append(note.trim());
            }
            return text.toString();
        }
    }

    private final OrderService orders;
    private final Runnable onCreated;

    private final List<DiningTable> tables;
    private final List<Area> areas;
    private final List<ArticleCategory> categories;
    private final List<Article> articles;

    private final ComboBox<AppUser> waiter = new ComboBox<>("Bedienung");
    private final Div areaButtons = TouchButton.grid("9rem");
    private final Div tableButtons = TouchButton.grid("6rem");
    private final VerticalLayout stepTable = new VerticalLayout();

    private final Span context = new Span();
    private final Div categoryRows = new Div();
    private final Div articleButtons = TouchButton.grid("10rem");
    private final Div courseButtons = new Div();
    private final Div cart = new Div();
    private final TextField orderNote = new TextField("Notiz zur Bestellung");
    private final VerticalLayout stepItems = new VerticalLayout();

    private final Button back = new Button("Zurück", VaadinIcon.ARROW_LEFT.create());
    private final Button create = new Button("Bestellung anlegen", VaadinIcon.CHECK.create());

    private final List<Line> lines = new ArrayList<>();
    private Area selectedArea;
    private DiningTable selectedTable;
    private List<ArticleCategory> categoryPath = new ArrayList<>();
    private Integer currentCourse;

    NewOrderDialog(OrderService orders, DiningTableRepository tableRepository, AreaRepository areaRepository,
            AppUserRepository users, ArticleCategoryRepository categoryRepository,
            ArticleRepository articleRepository, Runnable onCreated) {
        this.orders = orders;
        this.onCreated = onCreated;
        this.tables = tableRepository.findAll().stream().filter(DiningTable::isActive)
                .sorted(Comparator.comparing(DiningTable::getLabel)).toList();
        this.areas = areaRepository.findAll().stream().sorted(Comparator.comparing(Area::getName)).toList();
        this.categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        this.articles = articleRepository.findAll().stream().filter(Article::isActive)
                .sorted(Comparator.comparing(Article::getName)).toList();

        setHeaderTitle("Neue Bestellung");
        setWidth("62rem");
        setMaxWidth("96vw");
        setHeight("90vh");

        buildTableStep(users);
        buildItemStep();
        add(stepTable, stepItems);
        showStep(1);

        back.addClickListener(e -> showStep(1));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        create.addClickListener(e -> submit());
        back.addThemeVariants(ButtonVariant.LUMO_LARGE);
        Button cancel = new Button("Abbrechen", e -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_LARGE);
        getFooter().add(cancel, back, create);
    }

    // ------------------------------------------------------------ step 1: waiter, area, table

    private void buildTableStep(AppUserRepository users) {
        List<AppUser> waiters = users.findAllByActiveTrueOrderByDisplayNameAsc();
        waiter.setItems(waiters);
        waiter.setItemLabelGenerator(AppUser::getDisplayName);
        waiter.setRequiredIndicatorVisible(true);
        waiter.setHelperText("Die Bestellung wird auf diese Person gebucht.");
        waiter.setWidth("22rem");
        if (waiters.size() == 1) {
            waiter.setValue(waiters.get(0));
        }
        stepTable.setPadding(false);
        stepTable.add(waiter, new H4("Bereich"), areaButtons, new H4("Tisch"), tableButtons);
        if (areas.size() == 1) {
            selectedArea = areas.get(0);
        }
        renderTableStep();
    }

    private void renderTableStep() {
        areaButtons.removeAll();
        for (Area area : areas) {
            areaButtons.add(new TouchButton(area.getName(), null, area.getColor(), area.equals(selectedArea),
                    () -> {
                        selectedArea = area;
                        renderTableStep();
                    }));
        }
        tableButtons.removeAll();
        if (selectedArea == null) {
            tableButtons.add(new Span("Bitte zuerst einen Bereich wählen."));
            return;
        }
        for (DiningTable table : tables) {
            if (!table.getArea().equals(selectedArea)) {
                continue;
            }
            String color = table.getColor() != null ? table.getColor() : table.getArea().getColor();
            tableButtons.add(new TouchButton(table.getLabel(), null, color, table.equals(selectedTable),
                    () -> selectTable(table)));
        }
    }

    private void selectTable(DiningTable table) {
        if (waiter.getValue() == null) {
            Notification.show("Bitte zuerst die Bedienung wählen.", 3000, Notification.Position.MIDDLE);
            return;
        }
        selectedTable = table;
        showStep(2);
    }

    // ------------------------------------------------------------ step 2: categories, articles

    private void buildItemStep() {
        stepItems.setPadding(false);
        context.getStyle().set("font-size", "1.2rem").set("font-weight", "600");
        categoryRows.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0.5rem")
                .set("width", "100%");
        courseButtons.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "0.5rem");
        cart.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0.4rem")
                .set("width", "100%");
        orderNote.setMaxLength(500);
        orderNote.setWidthFull();
        stepItems.add(context, categoryRows, articleButtons, new H4("Gang für neue Positionen"), courseButtons,
                new H4("Bestellung"), cart, orderNote);
        if (!categories.isEmpty()) {
            categoryPath.add(roots().isEmpty() ? categories.get(0) : roots().get(0));
        }
        renderCourses();
        renderCategories();
        renderCart();
    }

    private List<ArticleCategory> roots() {
        return categories.stream().filter(c -> c.getParent() == null).toList();
    }

    private List<ArticleCategory> childrenOf(ArticleCategory parent) {
        return categories.stream().filter(c -> parent.equals(c.getParent())).toList();
    }

    private static String categoryColor(ArticleCategory category) {
        for (ArticleCategory c = category; c != null; c = c.getParent()) {
            if (c.getColor() != null) {
                return c.getColor();
            }
        }
        return null;
    }

    private static boolean isIn(ArticleCategory category, ArticleCategory ancestor) {
        for (ArticleCategory c = category; c != null; c = c.getParent()) {
            if (c.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private void selectCategory(int level, ArticleCategory category) {
        categoryPath = new ArrayList<>(categoryPath.subList(0, level));
        categoryPath.add(category);
        renderCategories();
    }

    private void renderCategories() {
        categoryRows.removeAll();
        List<ArticleCategory> level = roots();
        for (int depth = 0; !level.isEmpty(); depth++) {
            Div row = TouchButton.grid("8rem");
            final int rowDepth = depth;
            ArticleCategory selected = depth < categoryPath.size() ? categoryPath.get(depth) : null;
            for (ArticleCategory category : level) {
                row.add(new TouchButton(category.getName(), null, categoryColor(category),
                        category.equals(selected), () -> selectCategory(rowDepth, category)));
            }
            categoryRows.add(row);
            level = selected == null ? List.of() : childrenOf(selected);
        }
        articleButtons.removeAll();
        ArticleCategory current = categoryPath.isEmpty() ? null : categoryPath.get(categoryPath.size() - 1);
        for (Article article : articles) {
            if (current != null && !isIn(article.getCategory(), current)) {
                continue;
            }
            articleButtons.add(new TouchButton(article.getName(), article.getPrice() == null ? null
                    : article.getPrice().toPlainString(), categoryColor(article.getCategory()), false,
                    () -> addArticle(article)));
        }
    }

    private void renderCourses() {
        courseButtons.removeAll();
        Div none = new TouchButton("ohne", null, null, currentCourse == null, () -> {
            currentCourse = null;
            renderCourses();
        });
        none.getStyle().set("min-width", "6rem");
        courseButtons.add(none);
        for (int course = 1; course <= MAX_COURSE; course++) {
            final int value = course;
            Div button = new TouchButton("Gang " + course, null, null, Integer.valueOf(course).equals(currentCourse),
                    () -> {
                        currentCourse = value;
                        renderCourses();
                    });
            button.getStyle().set("min-width", "6rem");
            courseButtons.add(button);
        }
    }

    private void addArticle(Article article) {
        if (article.getModifierGroups().isEmpty()) {
            addLine(article, new LinkedHashSet<>(), null);
        } else {
            openOptions(article);
        }
    }

    private void addLine(Article article, Set<Modifier> modifiers, String note) {
        Set<Modifier> selected = new LinkedHashSet<>(modifiers);
        if (note == null || note.isBlank()) {
            for (Line line : lines) {
                if (line.sameAs(article, currentCourse, selected) && line.quantity < 99) {
                    line.quantity++;
                    renderCart();
                    return;
                }
            }
        }
        lines.add(new Line(article, 1, currentCourse, selected, note == null || note.isBlank() ? null : note.trim()));
        renderCart();
    }

    /** Modifiers and a note for an article that offers them. */
    private void openOptions(Article article) {
        Dialog options = new Dialog();
        options.setHeaderTitle(article.getName());
        options.setWidth("40rem");
        options.setMaxWidth("94vw");
        Set<Modifier> selected = new HashSet<>();
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        article.getModifierGroups().stream().sorted(Comparator.comparing(ModifierGroup::getName)).forEach(group -> {
            Div flow = TouchButton.grid("8rem");
            content.add(new H4(group.getName() + (group.isMultiSelect() ? "" : " (eine Auswahl)")), flow);
            renderModifiers(flow, group, selected);
        });
        TextField note = new TextField("Notiz");
        note.setMaxLength(500);
        note.setWidthFull();
        content.add(note);
        options.add(content);
        Button add = new Button("Hinzufügen", VaadinIcon.PLUS.create(), e -> {
            addLine(article, selected, note.getValue());
            options.close();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        Button cancel = new Button("Abbrechen", e -> options.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_LARGE);
        options.getFooter().add(cancel, add);
        options.open();
    }

    private void renderModifiers(Div flow, ModifierGroup group, Set<Modifier> selected) {
        flow.removeAll();
        for (Modifier modifier : group.getModifiers()) {
            flow.add(new TouchButton(modifier.getName(), null, null, selected.contains(modifier), () -> {
                if (selected.contains(modifier)) {
                    selected.remove(modifier);
                } else {
                    if (!group.isMultiSelect()) {
                        selected.removeAll(group.getModifiers());
                    }
                    selected.add(modifier);
                }
                renderModifiers(flow, group, selected);
            }));
        }
    }

    // ------------------------------------------------------------ cart

    private void renderCart() {
        cart.removeAll();
        if (lines.isEmpty()) {
            Span hint = new Span("Noch keine Positionen. Artikel antippen, um sie hinzuzufügen.");
            hint.getStyle().set("opacity", "0.7");
            cart.add(hint);
        }
        for (Line line : lines) {
            Span description = new Span(line.description());
            description.getStyle().set("flex", "1").set("font-size", "1.05rem");
            Span quantity = new Span(line.quantity + "×");
            quantity.getStyle().set("min-width", "2.5rem").set("text-align", "center").set("font-weight", "700")
                    .set("font-size", "1.2rem");
            HorizontalLayout row = new HorizontalLayout(description,
                    cartButton(VaadinIcon.MINUS, "Weniger", e -> changeQuantity(line, -1)), quantity,
                    cartButton(VaadinIcon.PLUS, "Mehr", e -> changeQuantity(line, 1)),
                    cartButton(VaadinIcon.EDIT, "Notiz", e -> editNote(line)),
                    cartButton(VaadinIcon.TRASH, "Entfernen", e -> {
                        lines.remove(line);
                        renderCart();
                    }));
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            cart.add(row);
        }
        create.setEnabled(!lines.isEmpty());
    }

    private static Button cartButton(VaadinIcon icon, String label,
            com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = new Button(icon.create(), listener);
        button.setAriaLabel(label);
        button.getStyle().set("min-width", "3.25rem").set("height", "3.25rem");
        return button;
    }

    private void changeQuantity(Line line, int delta) {
        int value = line.quantity + delta;
        if (value < 1) {
            lines.remove(line);
        } else if (value <= 99) {
            line.quantity = value;
        }
        renderCart();
    }

    private void editNote(Line line) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Notiz: " + line.article.getName());
        TextField note = new TextField();
        note.setMaxLength(500);
        note.setWidth("24rem");
        note.setValue(line.note == null ? "" : line.note);
        dialog.add(note);
        Button save = new Button("Speichern", e -> {
            line.note = note.getValue().isBlank() ? null : note.getValue().trim();
            dialog.close();
            renderCart();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Abbrechen", e -> dialog.close()), save);
        dialog.open();
        note.focus();
    }

    // ------------------------------------------------------------ navigation and submit

    private void showStep(int step) {
        stepTable.setVisible(step == 1);
        stepItems.setVisible(step == 2);
        back.setVisible(step == 2);
        create.setVisible(step == 2);
        if (step == 2) {
            context.setText("Tisch " + selectedTable.getLabel() + " · " + selectedTable.getArea().getName()
                    + " · " + waiter.getValue().getDisplayName());
        }
        renderTableStep();
    }

    private void submit() {
        if (selectedTable == null || waiter.getValue() == null) {
            Notification.show("Bitte Tisch und Bedienung auswählen.");
            return;
        }
        List<NewItem> items = lines.stream()
                .map(l -> new NewItem(l.article.getId(), l.quantity, l.note, l.course,
                        l.modifiers.stream().map(Modifier::getId).collect(Collectors.toSet())))
                .toList();
        String note = orderNote.getValue() == null || orderNote.getValue().isBlank() ? null
                : orderNote.getValue().trim();
        try {
            orders.submit(new SubmitOrder(UUID.randomUUID(), selectedTable.getId(), note, items),
                    waiter.getValue().getId());
        } catch (OrderRejectedException | OrderConflictException e) {
            Notification.show("Bestellung nicht angelegt: " + e.getMessage(), 8000, Notification.Position.MIDDLE);
            return;
        }
        Notification.show("Bestellung für Tisch " + selectedTable.getLabel() + " angelegt.", 4000,
                Notification.Position.MIDDLE);
        onCreated.run();
        close();
    }
}
