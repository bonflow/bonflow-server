package ch.famfrehner.bonflow.ui.admin;

import java.util.List;

import ch.famfrehner.bonflow.users.AdminUser;
import ch.famfrehner.bonflow.users.AdminUserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Accounts of the web UI. Every station display device gets its own account with the role
 * STATION, so a lost device can be locked out without touching the others. The password is
 * write-only: it is hashed on save and never shown again.
 */
@Route(value = "admin/accounts", layout = AdminLayout.class)
@PageTitle("Konten | BonFlow Admin")
@RolesAllowed("ADMIN")
public class AccountsView extends VerticalLayout {

    private final AdminUserService service;
    private final Grid<AdminUser> grid = new Grid<>();
    private final Binder<AdminUser> binder = new Binder<>();
    private final PasswordField password = new PasswordField("Passwort (mind. 8 Zeichen)");
    private final Dialog dialog = new Dialog();
    private final Button delete = new Button("Löschen", e -> delete());
    private AdminUser current;

    public AccountsView(AdminUserService service) {
        this.service = service;
        setSizeFull();

        Button add = new Button("Neu", VaadinIcon.PLUS.create(), e -> edit(new AdminUser()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(new H2("Konten"),
                new Paragraph("Admins und Manager verwalten das System; jedes Stationsgerät "
                        + "(Küche, Bar) meldet sich mit einem eigenen Konto der Rolle STATION an."),
                add, grid);

        grid.setSizeFull();
        grid.addColumn(AdminUser::getUsername).setHeader("Benutzername").setSortable(true);
        grid.addColumn(AdminUser::getRole).setHeader("Rolle").setSortable(true);
        grid.addItemClickListener(e -> edit(e.getItem()));

        TextField username = new TextField("Benutzername");
        binder.forField(username).asRequired("Benutzername erforderlich")
                .bind(AdminUser::getUsername, AdminUser::setUsername);
        Select<String> role = new Select<>();
        role.setLabel("Rolle");
        role.setItems(AdminUser.ADMIN, AdminUser.MANAGER, AdminUser.STATION);
        binder.forField(role).asRequired("Rolle erforderlich").bind(AdminUser::getRole, AdminUser::setRole);

        FormLayout form = new FormLayout(username, role, password);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        dialog.add(form);
        Button save = new Button("Speichern", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(delete, new Button("Abbrechen", e -> dialog.close()), save);

        refresh();
    }

    private void refresh() {
        List<AdminUser> items = service.all();
        grid.setItems(items);
    }

    private void edit(AdminUser user) {
        current = user;
        binder.readBean(user);
        password.clear();
        password.setHelperText(user.isNew() ? null : "Leer lassen, um das Passwort beizubehalten");
        delete.setVisible(!user.isNew());
        dialog.setHeaderTitle(user.isNew() ? "Neu anlegen" : "Bearbeiten");
        dialog.open();
    }

    private void save() {
        try {
            binder.writeBean(current);
            service.save(current, password.getValue());
        } catch (ValidationException e) {
            return;
        } catch (IllegalArgumentException e) {
            Notification.show(e.getMessage(), 5000, Notification.Position.MIDDLE);
            return;
        } catch (DataIntegrityViolationException e) {
            Notification.show("Speichern nicht möglich: Der Benutzername existiert bereits.", 5000,
                    Notification.Position.MIDDLE);
            refresh();
            return;
        }
        dialog.close();
        refresh();
    }

    private void delete() {
        try {
            service.delete(current);
        } catch (IllegalArgumentException e) {
            Notification.show(e.getMessage(), 5000, Notification.Position.MIDDLE);
            return;
        }
        dialog.close();
        refresh();
    }
}
