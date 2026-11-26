package ch.bonflow.ui.admin;

import ch.bonflow.masterdata.Area;
import ch.bonflow.masterdata.AreaRepository;
import ch.bonflow.masterdata.DiningTable;
import ch.bonflow.masterdata.DiningTableRepository;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/tables", layout = AdminLayout.class)
@PageTitle("Tische | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class TablesView extends CrudView<DiningTable> {

    private final AreaRepository areas;

    public TablesView(DiningTableRepository tables, AreaRepository areas) {
        super("Tische", tables, DiningTable::new);
        this.areas = areas;
        init();
    }

    @Override
    protected void configureGrid() {
        grid.addColumn(DiningTable::getLabel).setHeader("Tisch").setSortable(true);
        grid.addColumn(t -> t.getArea().getName()).setHeader("Bereich").setSortable(true);
        grid.addColumn(t -> t.isActive() ? "ja" : "nein").setHeader("Aktiv");
    }

    @Override
    protected void buildForm(FormLayout form) {
        TextField label = new TextField("Tisch (Nummer/Bezeichnung)");
        ComboBox<Area> area = new ComboBox<>("Bereich");
        area.setItems(areas.findAll());
        area.setItemLabelGenerator(Area::getName);
        Checkbox active = new Checkbox("Aktiv");
        binder.forField(label).asRequired("Bezeichnung erforderlich")
                .bind(DiningTable::getLabel, DiningTable::setLabel);
        binder.forField(area).asRequired("Bereich erforderlich")
                .bind(DiningTable::getArea, DiningTable::setArea);
        binder.forField(active).bind(DiningTable::isActive, DiningTable::setActive);
        form.add(label, area, active);
    }
}
