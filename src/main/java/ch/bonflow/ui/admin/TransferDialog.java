package ch.bonflow.ui.admin;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import ch.bonflow.masterdata.ImportResult;
import ch.bonflow.masterdata.InvalidTransferException;
import ch.bonflow.masterdata.MasterDataDocument;
import ch.bonflow.masterdata.MasterDataTransferService;
import ch.bonflow.masterdata.TransferSection;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.streams.UploadHandler;

/**
 * Export and import of master data as JSON. Shows the given sections; the user picks which of them
 * to export, and which of the sections found in an uploaded file to import.
 */
class TransferDialog extends Dialog {

    private final MasterDataTransferService service;
    private final List<TransferSection> offered;
    private final Runnable onImported;
    private final UI ui;

    private final CheckboxGroup<TransferSection> exportSections = new CheckboxGroup<>();
    private final CheckboxGroup<TransferSection> importSections = new CheckboxGroup<>();
    private final Paragraph importInfo = new Paragraph("Noch keine Datei ausgewählt.");
    private final Button importButton = new Button("Importieren", VaadinIcon.UPLOAD.create());
    private final Anchor download = new Anchor();
    private MasterDataDocument uploaded;

    TransferDialog(MasterDataTransferService service, List<TransferSection> offered, Runnable onImported) {
        this.service = service;
        this.offered = offered;
        this.onImported = onImported;
        this.ui = UI.getCurrent();
        setHeaderTitle("Import / Export");
        setWidth("32rem");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.add(new H4("Exportieren"), exportPart(), new H4("Importieren"), importPart());
        add(content);
        getFooter().add(new Button("Schließen", e -> close()));
    }

    private VerticalLayout exportPart() {
        exportSections.setItems(offered);
        exportSections.setItemLabelGenerator(TransferSection::label);
        exportSections.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        exportSections.setValue(new LinkedHashSet<>(offered));

        download.getElement().setAttribute("download", true);
        download.getStyle().set("display", "none");

        Button export = new Button("Als JSON herunterladen", VaadinIcon.DOWNLOAD.create(), e -> export());
        export.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout part = new VerticalLayout(exportSections, export, download);
        part.setPadding(false);
        return part;
    }

    private VerticalLayout importPart() {
        importSections.setItemLabelGenerator(TransferSection::label);
        importSections.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        importSections.setVisible(false);
        importButton.setEnabled(false);
        importButton.addClickListener(e -> doImport());

        Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> ui.access(() -> read(data))));
        upload.setAcceptedFileTypes(".json", "application/json");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(10 * 1024 * 1024);
        upload.setDropAllowed(true);

        Paragraph hint = new Paragraph("Vorhandene Einträge mit gleichem Namen werden aktualisiert, "
                + "neue angelegt. Es wird nichts gelöscht.");
        VerticalLayout part = new VerticalLayout(hint, upload, importInfo, importSections, importButton);
        part.setPadding(false);
        return part;
    }

    private void export() {
        Set<TransferSection> selected = exportSections.getValue();
        if (selected.isEmpty()) {
            Notification.show("Bitte mindestens einen Bereich auswählen.");
            return;
        }
        byte[] bytes = service.export(selected);
        String name = "bonflow-stammdaten-" + LocalDate.now() + ".json";
        download.setHref(new StreamResource(name, () -> new ByteArrayInputStream(bytes)));
        download.getElement().executeJs("this.click()");
    }

    private void read(byte[] data) {
        try {
            uploaded = service.parse(data);
        } catch (InvalidTransferException e) {
            uploaded = null;
            importSections.setVisible(false);
            importButton.setEnabled(false);
            importInfo.setText(e.getMessage());
            return;
        }
        Set<TransferSection> available = service.sectionsIn(uploaded).stream()
                .filter(offered::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (available.isEmpty()) {
            importSections.setVisible(false);
            importButton.setEnabled(false);
            importInfo.setText("Die Datei enthält keine Daten, die hier importiert werden können.");
            return;
        }
        importSections.setItems(available);
        importSections.setValue(new LinkedHashSet<>(available));
        importSections.setVisible(true);
        importButton.setEnabled(true);
        importInfo.setText("Datei gelesen. Welche Bereiche sollen importiert werden?");
    }

    private void doImport() {
        Set<TransferSection> selected = importSections.getValue();
        if (uploaded == null || selected.isEmpty()) {
            Notification.show("Bitte mindestens einen Bereich auswählen.");
            return;
        }
        try {
            ImportResult result = service.importData(uploaded, selected);
            Notification.show("Import abgeschlossen: " + summary(result.counts()), 8000,
                    Notification.Position.MIDDLE);
            onImported.run();
            close();
        } catch (InvalidTransferException e) {
            Notification.show("Import abgebrochen, es wurde nichts geändert. " + e.getMessage(), 10000,
                    Notification.Position.MIDDLE);
        } catch (RuntimeException e) {
            Notification.show("Import abgebrochen, es wurde nichts geändert: Die Daten verletzen eine Bedingung "
                    + "der Datenbank (zum Beispiel doppelte Namen oder zu lange Werte).", 10000,
                    Notification.Position.MIDDLE);
        }
    }

    private static String summary(Map<TransferSection, ImportResult.Counts> counts) {
        return counts.entrySet().stream()
                .map(e -> e.getKey().label() + " (" + e.getValue().created() + " neu, "
                        + e.getValue().updated() + " aktualisiert)")
                .collect(Collectors.joining(", "));
    }
}
