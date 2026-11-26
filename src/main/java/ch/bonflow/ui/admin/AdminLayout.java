package ch.bonflow.ui.admin;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import ch.bonflow.users.AdminUserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Navigation shell of the admin UI. Vaadin also checks the access rules of the parent layout, so it
 * needs its own annotation; the individual views narrow it down (e.g. accounts: ADMIN only).
 */
@RolesAllowed({"ADMIN", "MANAGER"})
public class AdminLayout extends AppLayout {

    private final AdminUserService accounts;

    public AdminLayout(AdminUserService accounts, AuthenticationContext authentication,
            ObjectProvider<BuildProperties> build) {
        this.accounts = accounts;
        H1 title = new H1("BonFlow Admin");
        title.getStyle().set("font-size", "1.25rem").set("margin", "0");

        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? "" : auth.getName();
        Span user = new Span(username);
        Button password = new Button("Passwort ändern", e -> openPasswordDialog(username));
        password.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button logout = new Button("Abmelden", e -> authentication.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout account = new HorizontalLayout(user, password, logout);
        account.setAlignItems(FlexComponent.Alignment.CENTER);
        account.getStyle().set("margin-left", "auto").set("padding-right", "1rem");
        addToNavbar(new DrawerToggle(), title, account);

        SideNav nav = new SideNav();
        nav.addItem(
                new SideNavItem("Start", AdminHomeView.class, VaadinIcon.HOME.create()),
                new SideNavItem("Bereiche", AreasView.class, VaadinIcon.LAYOUT.create()),
                new SideNavItem("Tische", TablesView.class, VaadinIcon.TABLE.create()),
                new SideNavItem("Drucker", PrintersView.class, VaadinIcon.PRINT.create()),
                new SideNavItem("Stationen", StationsView.class, VaadinIcon.COG.create()),
                new SideNavItem("Kategorien", CategoriesView.class, VaadinIcon.TAGS.create()),
                new SideNavItem("Artikel", ArticlesView.class, VaadinIcon.CART.create()),
                new SideNavItem("Modifier", ModifierGroupsView.class, VaadinIcon.OPTIONS.create()),
                new SideNavItem("Mitarbeitende", WaitersView.class, VaadinIcon.USERS.create()),
                new SideNavItem("Konten", AccountsView.class, VaadinIcon.KEY.create()),
                new SideNavItem("Bestellungen", OrdersView.class, VaadinIcon.LIST.create()),
                new SideNavItem("Druck-Status", PrintStatusView.class, VaadinIcon.PRINT.create()));

        // Build info only exists in a Maven build; a plain IDE run shows "dev".
        BuildProperties props = build.getIfAvailable();
        String version = props == null ? "dev" : props.getVersion();
        Div footer = new Div(new Span("BonFlow " + version), new Span(" · "),
                link("GitHub", "https://github.com/bonflow/bonflow-server"), new Span(" · "),
                link("bonflow.ch", "https://bonflow.ch"));
        footer.getStyle().set("font-size", "0.85rem").set("padding", "0.75rem 1rem").set("opacity", "0.8");
        VerticalLayout drawer = new VerticalLayout(nav, footer);
        drawer.setPadding(false);
        drawer.setSpacing(false);
        drawer.setHeightFull();
        drawer.setFlexGrow(1, nav);
        addToDrawer(drawer);
    }

    private static Anchor link(String text, String href) {
        Anchor a = new Anchor(href, text);
        a.setTarget("_blank");
        a.getElement().setAttribute("rel", "noopener noreferrer");
        return a;
    }

    private void openPasswordDialog(String username) {
        PasswordField current = new PasswordField("Aktuelles Passwort");
        PasswordField next = new PasswordField("Neues Passwort (mind. 8 Zeichen)");
        PasswordField repeat = new PasswordField("Neues Passwort wiederholen");
        for (PasswordField field : new PasswordField[] {current, next, repeat}) {
            field.setWidthFull();
            // The eye button would take a tab stop; Tab should go straight to the next field.
            field.setRevealButtonVisible(false);
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Passwort ändern");
        dialog.setWidth("24rem");
        dialog.setMaxWidth("100%");
        VerticalLayout form = new VerticalLayout(current, next, repeat);
        form.setPadding(false);
        form.setSpacing(true);
        dialog.add(form);
        Button save = new Button("Speichern", e -> {
            if (!next.getValue().equals(repeat.getValue())) {
                Notification.show("Die neuen Passwörter stimmen nicht überein.", 4000,
                        Notification.Position.MIDDLE);
                return;
            }
            try {
                accounts.changePassword(username, current.getValue(), next.getValue());
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
                return;
            }
            dialog.close();
            Notification.show("Passwort geändert.", 3000, Notification.Position.BOTTOM_END);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Abbrechen", e -> dialog.close()), save);
        dialog.open();
        current.focus();
    }
}
