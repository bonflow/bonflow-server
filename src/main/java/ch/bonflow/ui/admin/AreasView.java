package ch.bonflow.ui.admin;

import ch.bonflow.masterdata.MasterDataTransferService;
import ch.bonflow.masterdata.TransferSection;
import ch.bonflow.masterdata.Area;
import ch.bonflow.masterdata.AreaRepository;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/areas", layout = AdminLayout.class)
@PageTitle("Bereiche | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class AreasView extends CrudView<Area> {

    public AreasView(AreaRepository areas, MasterDataTransferService transfer) {
        super("Bereiche", areas, Area::new);
        enableTransfer(transfer, TransferSection.AREAS);
        init();
    }

    @Override
    protected void configureGrid() {
        grid.addColumn(Area::getName).setHeader("Name").setSortable(true);
    }

    @Override
    protected void buildForm(FormLayout form) {
        TextField name = new TextField("Name");
        binder.forField(name).asRequired("Name erforderlich").bind(Area::getName, Area::setName);
        form.add(name);
    }
}
