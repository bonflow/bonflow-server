package ch.bonflow.ui.admin;

import java.util.stream.Collectors;

import ch.bonflow.masterdata.MasterDataTransferService;
import ch.bonflow.masterdata.TransferSection;
import ch.bonflow.masterdata.Printer;
import ch.bonflow.masterdata.PrinterRepository;
import ch.bonflow.masterdata.Station;
import ch.bonflow.masterdata.StationRepository;
import ch.bonflow.ui.station.StationView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/stations", layout = AdminLayout.class)
@PageTitle("Stationen | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class StationsView extends CrudView<Station> {

    private final PrinterRepository printers;

    public StationsView(StationRepository stations, PrinterRepository printers,
            MasterDataTransferService transfer) {
        super("Stationen", stations, Station::new);
        this.printers = printers;
        enableTransfer(transfer, TransferSection.PRINTERS, TransferSection.STATIONS);
        init();
    }

    @Override
    protected void configureGrid() {
        grid.addColumn(Station::getName).setHeader("Name").setSortable(true);
        grid.addColumn(s -> s.getPrinters().stream().map(Printer::getName)
                .sorted().collect(Collectors.joining(", "))).setHeader("Drucker");
        grid.addColumn(s -> s.isHasDisplay() ? "ja" : "nein").setHeader("Display");
        grid.addComponentColumn(StationsView::displayLink).setHeader("Anzeige").setAutoWidth(true);
    }

    /** Link to the station display, opened in its own tab (it is meant to run full screen). */
    private static Component displayLink(Station station) {
        if (!station.isHasDisplay()) {
            return new Span("–");
        }
        String url = RouteConfiguration.forApplicationScope()
                .getUrl(StationView.class, new RouteParameters("stationId", String.valueOf(station.getId())));
        Anchor link = new Anchor(url, "Anzeige öffnen");
        link.setTarget("_blank");
        return link;
    }

    @Override
    protected void buildForm(FormLayout form) {
        TextField name = new TextField("Name (z.B. Küche, Bar)");
        MultiSelectComboBox<Printer> printerBox = new MultiSelectComboBox<>("Drucker");
        printerBox.setItems(printers.findAll());
        printerBox.setItemLabelGenerator(Printer::getName);
        Checkbox display = new Checkbox("Hat ein Display");
        binder.forField(name).asRequired("Name erforderlich").bind(Station::getName, Station::setName);
        binder.forField(printerBox).bind(Station::getPrinters, Station::setPrinters);
        binder.forField(display).bind(Station::isHasDisplay, Station::setHasDisplay);
        form.add(name, printerBox, display);
    }
}
