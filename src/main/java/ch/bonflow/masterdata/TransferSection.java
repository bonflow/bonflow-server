package ch.bonflow.masterdata;

/** A part of the master data that can be exported and imported on its own. */
public enum TransferSection {
    AREAS("Bereiche"),
    TABLES("Tische"),
    PRINTERS("Drucker"),
    STATIONS("Stationen"),
    MODIFIER_GROUPS("Modifier-Gruppen"),
    CATEGORIES("Kategorien"),
    ARTICLES("Artikel");

    private final String label;

    TransferSection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
