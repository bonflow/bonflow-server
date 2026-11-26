package ch.bonflow.ui.admin;

import ch.bonflow.printing.PrintAdminService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.RolesAllowed;

/** Entry point of the admin UI. CRUD views (tables, articles, stations, users) hang off this. */
@Route(value = "admin", layout = AdminLayout.class)
@PageTitle("Admin | BonFlow")
@RolesAllowed({"ADMIN", "MANAGER"})
public class AdminHomeView extends VerticalLayout {

    public AdminHomeView(PrintAdminService printAdmin) {
        add(new H1("BonFlow Admin"), new Paragraph("Stammdaten verwalten: Bereiche, Tische, Drucker, Stationen, Kategorien, Artikel und Modifier."));
        if (printAdmin.anyPrinterOffline()) {
            Div warning = new Div(new Span("⚠ Mindestens ein Drucker ist nicht erreichbar. "),
                    new RouterLink("Druck-Status ansehen", PrintStatusView.class));
            warning.getStyle().set("border", "2px solid var(--lumo-error-color)")
                    .set("color", "var(--lumo-error-text-color)").set("padding", "var(--lumo-space-m)")
                    .set("border-radius", "var(--lumo-border-radius-m)");
            add(warning);
        }
    }
}
