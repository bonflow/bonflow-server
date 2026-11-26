package ch.bonflow.ui.admin;

import ch.bonflow.masterdata.SettingsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/** Venue wide settings. Changes reach the apps with the next master data refresh. */
@Route(value = "admin/settings", layout = AdminLayout.class)
@PageTitle("Einstellungen | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class SettingsView extends VerticalLayout {

    public SettingsView(SettingsService settings) {
        Checkbox courses = new Checkbox("Gänge bei Bestellungen verwenden");
        courses.setHelperText("Ausgeschaltet verschwindet die Gang-Auswahl in der App und im Bestelldialog.");
        courses.setValue(settings.coursesEnabled());
        Button save = new Button("Speichern", e -> {
            settings.setCoursesEnabled(courses.getValue());
            Notification.show("Einstellungen gespeichert.", 3000, Notification.Position.BOTTOM_END);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(new H2("Einstellungen"), courses, save);
    }
}
