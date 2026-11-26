package ch.bonflow.ui;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Start page {@code /}: sends admins and managers to the admin UI; everyone else gets a hint. */
@Route("")
@PageTitle("BonFlow")
@PermitAll
public class RootView extends VerticalLayout implements BeforeEnterObserver {

    public RootView() {
        add(new Paragraph("Dieses Konto hat keinen Zugriff auf die Verwaltung. "
                + "Die Stationsanzeige wird direkt über ihren Link geöffnet (/station/<id>)."));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
        if (admin) {
            event.forwardTo("admin");
        }
    }
}
