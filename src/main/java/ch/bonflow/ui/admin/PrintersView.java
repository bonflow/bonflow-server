package ch.bonflow.ui.admin;

import ch.bonflow.masterdata.MasterDataTransferService;
import ch.bonflow.masterdata.TransferSection;
import ch.bonflow.masterdata.Printer;
import ch.bonflow.masterdata.PrinterProtocol;
import ch.bonflow.masterdata.PrinterRepository;
import ch.bonflow.printing.PrintAdminService;
import ch.bonflow.printing.TestPrintResult;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/printers", layout = AdminLayout.class)
@PageTitle("Drucker | BonFlow Admin")
@RolesAllowed({"ADMIN", "MANAGER"})
public class PrintersView extends CrudView<Printer> {

    private final PrintAdminService printAdmin;

    public PrintersView(PrinterRepository printers, PrintAdminService printAdmin, MasterDataTransferService transfer) {
        super("Drucker", printers, Printer::new);
        this.printAdmin = printAdmin;
        enableTransfer(transfer, TransferSection.PRINTERS);
        init();
    }

    @Override
    protected void configureGrid() {
        grid.addColumn(Printer::getName).setHeader("Name").setSortable(true);
        grid.addColumn(p -> p.getHost() + ":" + p.getPort()).setHeader("Adresse");
        grid.addColumn(Printer::getProtocol).setHeader("Protokoll");
        grid.addColumn(p -> p.getPaperWidthMm() + " mm").setHeader("Papier");
        grid.addColumn(p -> p.isEnabled() ? "ja" : "nein").setHeader("Aktiv");
        grid.addComponentColumn(p -> {
            Button test = new Button("Testdruck", VaadinIcon.PRINT.create(), e -> testPrint(p));
            test.addThemeVariants(ButtonVariant.LUMO_SMALL);
            return test;
        }).setHeader("");
    }

    private void testPrint(Printer printer) {
        TestPrintResult result = printAdmin.testPrint(printer.getId());
        Notification notification = Notification.show(
                printer.getName() + ": " + result.message(), 5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(result.success()
                ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);
    }

    @Override
    protected void buildForm(FormLayout form) {
        TextField name = new TextField("Name");
        TextField host = new TextField("Host / IP-Adresse");
        IntegerField port = new IntegerField("Port");
        port.setMin(1);
        port.setMax(65535);
        ComboBox<PrinterProtocol> protocol = new ComboBox<>("Protokoll");
        protocol.setItems(PrinterProtocol.values());
        protocol.setAllowCustomValue(false);
        IntegerField paperWidth = new IntegerField("Papierbreite (mm)");
        paperWidth.setMin(40);
        paperWidth.setMax(120);
        Checkbox enabled = new Checkbox("Aktiv");

        binder.forField(name).asRequired("Name erforderlich").bind(Printer::getName, Printer::setName);
        binder.forField(host).asRequired("Host erforderlich").bind(Printer::getHost, Printer::setHost);
        binder.forField(port).asRequired("Port erforderlich")
                .bind(Printer::getPort, (p, v) -> p.setPort(v));
        binder.forField(protocol).asRequired("Protokoll erforderlich")
                .bind(Printer::getProtocol, Printer::setProtocol);
        binder.forField(paperWidth).asRequired("Papierbreite erforderlich")
                .bind(Printer::getPaperWidthMm, (p, v) -> p.setPaperWidthMm(v));
        binder.forField(enabled).bind(Printer::isEnabled, Printer::setEnabled);
        form.add(name, host, port, protocol, paperWidth, enabled);
    }
}
