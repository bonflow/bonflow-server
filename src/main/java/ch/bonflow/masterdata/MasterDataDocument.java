package ch.bonflow.masterdata;

import java.math.BigDecimal;
import java.util.List;

/**
 * The JSON format of the master data export. References between entries use names instead of
 * database ids, so a file can be imported into another installation: tables name their area,
 * stations their printers, categories their parent path, articles their category path.
 * Sections that were not exported are {@code null}.
 */
public record MasterDataDocument(
        String format,
        int version,
        String exportedAt,
        List<AreaEntry> areas,
        List<TableEntry> tables,
        List<PrinterEntry> printers,
        List<StationEntry> stations,
        List<ModifierGroupEntry> modifierGroups,
        List<CategoryEntry> categories,
        List<ArticleEntry> articles) {

    public record AreaEntry(String name) {
    }

    public record TableEntry(String label, String area, Boolean active) {
    }

    public record PrinterEntry(String name, String host, Integer port, String protocol, Integer paperWidthMm,
            Boolean enabled) {
    }

    public record StationEntry(String name, Boolean hasDisplay, List<String> printers) {
    }

    public record ModifierGroupEntry(String name, Boolean multiSelect, List<String> modifiers) {
    }

    /** @param path names from the top category down to this one, e.g. ["Getränke", "Bier"] */
    public record CategoryEntry(List<String> path, Integer sortOrder, String defaultStation) {
    }

    /** @param category path of the category, see {@link CategoryEntry} */
    public record ArticleEntry(String name, String shortName, List<String> category, BigDecimal price,
            Boolean active, String stationOverride, List<String> modifierGroups) {
    }
}
