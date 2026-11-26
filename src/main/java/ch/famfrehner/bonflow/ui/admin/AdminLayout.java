package ch.famfrehner.bonflow.ui.admin;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;

/** Navigation shell of the admin UI. */
public class AdminLayout extends AppLayout {

    public AdminLayout() {
        H1 title = new H1("BonFlow Admin");
        title.getStyle().set("font-size", "1.25rem").set("margin", "0");
        addToNavbar(new DrawerToggle(), title);

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
                new SideNavItem("Mitarbeitende", WaitersView.class, VaadinIcon.USERS.create()));
        addToDrawer(nav);
    }
}
