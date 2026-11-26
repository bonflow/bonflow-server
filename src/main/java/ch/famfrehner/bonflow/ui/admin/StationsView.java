package ch.famfrehner.bonflow.ui.admin;

import java.util.stream.Collectors;

import ch.famfrehner.bonflow.masterdata.Printer;
import ch.famfrehner.bonflow.masterdata.PrinterRepository;
import ch.famfrehner.bonflow.masterdata.Station;
import ch.famfrehner.bonflow.masterdata.StationRepository;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/stations", layout = AdminLayout.class)
@PageTitle("Stationen | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class StationsView extends CrudView<Station> {

    private final PrinterRepository printers;

    public StationsView(StationRepository stations, PrinterRepository printers) {
        super("Stationen", stations, Station::new);
        this.printers = printers;
        init();
    }

    @Override
    protected void configureGrid() {
        grid.addColumn(Station::getName).setHeader("Name").setSortable(true);
        grid.addColumn(s -> s.getPrinters().stream().map(Printer::getName)
                .sorted().collect(Collectors.joining(", "))).setHeader("Drucker");
        grid.addColumn(s -> s.isHasDisplay() ? "ja" : "nein").setHeader("Display");
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
