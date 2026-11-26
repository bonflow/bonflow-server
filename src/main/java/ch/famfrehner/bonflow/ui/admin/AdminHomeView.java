package ch.famfrehner.bonflow.ui.admin;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/** Entry point of the admin UI. CRUD views (tables, articles, stations, users) hang off this. */
@Route(value = "admin", layout = AdminLayout.class)
@PageTitle("Admin | BonFlow")
@RolesAllowed({"ADMIN", "MANAGER"})
public class AdminHomeView extends VerticalLayout {

    public AdminHomeView() {
        add(new H1("BonFlow Admin"), new Paragraph("Stammdaten verwalten: Bereiche, Tische, Drucker, Stationen, Kategorien, Artikel und Modifier."));
    }
}
