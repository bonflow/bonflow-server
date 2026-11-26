package ch.famfrehner.bonflow.ui.admin;

import ch.famfrehner.bonflow.users.AppUser;
import ch.famfrehner.bonflow.users.AppUserRepository;
import ch.famfrehner.bonflow.users.WaiterService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataIntegrityViolationException;

/** Waiters and their PINs. The PIN is write-only: it is hashed on save and never shown again. */
@Route(value = "admin/waiters", layout = AdminLayout.class)
@PageTitle("Mitarbeitende | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class WaitersView extends VerticalLayout {

    private final AppUserRepository repository;
    private final WaiterService waiters;
    private final Grid<AppUser> grid = new Grid<>();
    private final Binder<AppUser> binder = new Binder<>();
    private final PasswordField pin = new PasswordField("PIN (4–8 Ziffern)");
    private final Dialog dialog = new Dialog();
    private AppUser current;

    public WaitersView(AppUserRepository repository, WaiterService waiters) {
        this.repository = repository;
        this.waiters = waiters;
        setSizeFull();

        Button add = new Button("Neu", VaadinIcon.PLUS.create(), e -> edit(new AppUser()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(new H2("Mitarbeitende"), add, grid);

        grid.setSizeFull();
        grid.addColumn(AppUser::getDisplayName).setHeader("Name").setSortable(true);
        grid.addColumn(AppUser::getLogin).setHeader("Login");
        grid.addColumn(u -> u.isActive() ? "aktiv" : "inaktiv").setHeader("Status");
        grid.addItemClickListener(e -> edit(e.getItem()));

        TextField name = new TextField("Name");
        binder.forField(name).asRequired("Name erforderlich")
                .bind(AppUser::getDisplayName, AppUser::setDisplayName);
        TextField login = new TextField("Login");
        binder.forField(login).asRequired("Login erforderlich").bind(AppUser::getLogin, AppUser::setLogin);
        Checkbox active = new Checkbox("Aktiv");
        binder.forField(active).bind(AppUser::isActive, AppUser::setActive);

        FormLayout form = new FormLayout(name, login, pin, active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        dialog.add(form);
        Button save = new Button("Speichern", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Abbrechen", e -> dialog.close()), save);

        refresh();
    }

    private void refresh() {
        grid.setItems(repository.findAllByOrderByDisplayNameAsc());
    }

    private void edit(AppUser user) {
        current = user;
        binder.readBean(user);
        pin.clear();
        pin.setHelperText(user.isNew() ? null : "Leer lassen, um die PIN beizubehalten");
        dialog.setHeaderTitle(user.isNew() ? "Neu anlegen" : "Bearbeiten");
        dialog.open();
    }

    private void save() {
        try {
            binder.writeBean(current);
            waiters.save(current, pin.getValue());
        } catch (ValidationException e) {
            return;
        } catch (IllegalArgumentException e) {
            Notification.show(e.getMessage(), 5000, Notification.Position.MIDDLE);
            return;
        } catch (DataIntegrityViolationException e) {
            Notification.show("Speichern nicht möglich: Der Login existiert bereits.", 5000,
                    Notification.Position.MIDDLE);
            refresh();
            return;
        }
        dialog.close();
        refresh();
    }
}
