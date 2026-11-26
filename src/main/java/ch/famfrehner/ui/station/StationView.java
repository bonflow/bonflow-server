package ch.famfrehner.ui.station;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/**
 * Station display, one browser (kiosk) per station at {@code /station/{stationId}}.
 * Ticket cards and the push-based StationBroadcaster registration come with the display module.
 */
@Route("station/:stationId")
@PageTitle("Station | BonFlow")
@RolesAllowed({"STATION", "ADMIN", "MANAGER"})
public class StationView extends VerticalLayout implements BeforeEnterObserver {

    private final H1 title = new H1("Station");

    public StationView() {
        add(title);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getRouteParameters().get("stationId")
                .ifPresent(id -> title.setText("Station " + id));
    }
}
