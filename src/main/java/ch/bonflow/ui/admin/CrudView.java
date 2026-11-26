package ch.bonflow.ui.admin;

import java.util.List;
import java.util.function.Supplier;

import ch.bonflow.masterdata.AbstractEntity;
import ch.bonflow.masterdata.MasterDataTransferService;
import ch.bonflow.masterdata.TransferSection;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Grid with a create/edit dialog for one master data entity. Subclasses define the columns and
 * the form fields and call {@link #init()} at the end of their constructor.
 */
abstract class CrudView<T extends AbstractEntity> extends VerticalLayout {

    protected final Grid<T> grid = new Grid<>();
    protected final Binder<T> binder = new Binder<>();

    private final JpaRepository<T, Long> repository;
    private final Supplier<T> factory;
    private final Dialog dialog = new Dialog();
    private final Button delete = new Button("Löschen");
    private final HorizontalLayout toolbar = new HorizontalLayout();
    private T current;

    protected CrudView(String title, JpaRepository<T, Long> repository, Supplier<T> factory) {
        this.repository = repository;
        this.factory = factory;
        setSizeFull();
        Button add = new Button("Neu", VaadinIcon.PLUS.create(), e -> edit(this.factory.get()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        toolbar.add(add);
        add(new H2(title), toolbar, grid);
        grid.setSizeFull();
        grid.addItemClickListener(e -> edit(e.getItem()));
    }

    /** Adds an import/export button for the given master data sections to the toolbar. */
    protected final void enableTransfer(MasterDataTransferService transfer, TransferSection... sections) {
        Button button = new Button("Import / Export", VaadinIcon.EXCHANGE.create(),
                e -> new TransferDialog(transfer, List.of(sections), this::refresh).open());
        toolbar.add(button);
    }

    /** Adds the grid columns. */
    protected abstract void configureGrid();

    /** Adds the form fields and binds them to {@link #binder}. */
    protected abstract void buildForm(FormLayout form);

    /** Rejects invalid combinations; throw {@link IllegalArgumentException} with a user message. */
    protected void beforeSave(T item) {
    }

    protected List<T> loadItems() {
        return repository.findAll();
    }

    protected final void init() {
        configureGrid();

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        buildForm(form);
        dialog.add(form);

        Button save = new Button("Speichern", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Abbrechen", e -> dialog.close());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.addClickListener(e -> confirmDelete());
        dialog.getFooter().add(delete, cancel, save);

        refresh();
    }

    protected final void refresh() {
        grid.setItems(loadItems());
    }

    private void edit(T item) {
        current = item;
        binder.readBean(item);
        dialog.setHeaderTitle(item.isNew() ? "Neu anlegen" : "Bearbeiten");
        delete.setVisible(!item.isNew());
        dialog.open();
    }

    private void save() {
        try {
            binder.writeBean(current);
        } catch (ValidationException e) {
            return;
        }
        try {
            beforeSave(current);
            repository.save(current);
        } catch (IllegalArgumentException e) {
            notify(e.getMessage());
            return;
        } catch (DataIntegrityViolationException e) {
            notify("Speichern nicht möglich: Der Wert existiert bereits oder verletzt eine Bedingung.");
            refresh();
            return;
        }
        dialog.close();
        refresh();
    }

    private void confirmDelete() {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Wirklich löschen?");
        confirm.setText("Der Eintrag wird entfernt.");
        confirm.setCancelable(true);
        confirm.setConfirmText("Löschen");
        confirm.addConfirmListener(e -> {
            try {
                repository.delete(current);
                dialog.close();
            } catch (DataIntegrityViolationException ex) {
                notify("Löschen nicht möglich: Der Eintrag wird noch verwendet.");
            }
            refresh();
        });
        confirm.open();
    }

    private static void notify(String message) {
        Notification.show(message, 5000, Notification.Position.MIDDLE);
    }
}
