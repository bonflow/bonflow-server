package ch.bonflow.masterdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.bonflow.masterdata.MasterDataDocument.AreaEntry;
import ch.bonflow.masterdata.MasterDataDocument.ArticleEntry;
import ch.bonflow.masterdata.MasterDataDocument.CategoryEntry;
import ch.bonflow.masterdata.MasterDataDocument.ModifierGroupEntry;
import ch.bonflow.masterdata.MasterDataDocument.PrinterEntry;
import ch.bonflow.masterdata.MasterDataDocument.StationEntry;
import ch.bonflow.masterdata.MasterDataDocument.TableEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Export and import of the master data as JSON.
 *
 * <p>Import matches entries by name (tables by label, categories by path, articles by category
 * path and name): existing entries are updated, missing ones are created, nothing is deleted. The
 * whole import runs in one transaction, so an invalid file changes nothing. References must exist,
 * either in the file's imported sections or already in the database.
 */
@Service
public class MasterDataTransferService {

    public static final String FORMAT = "bonflow-masterdata";
    public static final int VERSION = 1;

    private final AreaRepository areaRepository;
    private final DiningTableRepository tableRepository;
    private final PrinterRepository printerRepository;
    private final StationRepository stationRepository;
    private final ModifierGroupRepository groupRepository;
    private final ArticleCategoryRepository categoryRepository;
    private final ArticleRepository articleRepository;
    private final JsonMapper json;

    MasterDataTransferService(AreaRepository areaRepository, DiningTableRepository tableRepository,
            PrinterRepository printerRepository, StationRepository stationRepository,
            ModifierGroupRepository groupRepository, ArticleCategoryRepository categoryRepository,
            ArticleRepository articleRepository, JsonMapper json) {
        this.areaRepository = areaRepository;
        this.tableRepository = tableRepository;
        this.printerRepository = printerRepository;
        this.stationRepository = stationRepository;
        this.groupRepository = groupRepository;
        this.categoryRepository = categoryRepository;
        this.articleRepository = articleRepository;
        this.json = json;
    }

    // ------------------------------------------------------------------ export

    /** @return the selected sections as pretty printed JSON */
    @Transactional(readOnly = true)
    public byte[] export(Set<TransferSection> sections) {
        MasterDataDocument document = new MasterDataDocument(FORMAT, VERSION, Instant.now().toString(),
                sections.contains(TransferSection.AREAS) ? exportAreas() : null,
                sections.contains(TransferSection.TABLES) ? exportTables() : null,
                sections.contains(TransferSection.PRINTERS) ? exportPrinters() : null,
                sections.contains(TransferSection.STATIONS) ? exportStations() : null,
                sections.contains(TransferSection.MODIFIER_GROUPS) ? exportGroups() : null,
                sections.contains(TransferSection.CATEGORIES) ? exportCategories() : null,
                sections.contains(TransferSection.ARTICLES) ? exportArticles() : null);
        return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
    }

    private List<AreaEntry> exportAreas() {
        return areaRepository.findAll().stream().sorted(Comparator.comparing(Area::getName))
                .map(a -> new AreaEntry(a.getName(), a.getColor())).toList();
    }

    private List<TableEntry> exportTables() {
        return tableRepository.findAll().stream().sorted(Comparator.comparing(DiningTable::getLabel))
                .map(t -> new TableEntry(t.getLabel(), t.getArea().getName(), t.isActive(), t.getColor())).toList();
    }

    private List<PrinterEntry> exportPrinters() {
        return printerRepository.findAll().stream().sorted(Comparator.comparing(Printer::getName))
                .map(p -> new PrinterEntry(p.getName(), p.getHost(), p.getPort(), p.getProtocol().name(),
                        p.getPaperWidthMm(), p.isEnabled()))
                .toList();
    }

    private List<StationEntry> exportStations() {
        return stationRepository.findAll().stream().sorted(Comparator.comparing(Station::getName))
                .map(s -> new StationEntry(s.getName(), s.isHasDisplay(),
                        s.getPrinters().stream().map(Printer::getName).sorted().toList()))
                .toList();
    }

    private List<ModifierGroupEntry> exportGroups() {
        return groupRepository.findAll().stream().sorted(Comparator.comparing(ModifierGroup::getName))
                .map(g -> new ModifierGroupEntry(g.getName(), g.isMultiSelect(), g.getModifierNames())).toList();
    }

    private List<CategoryEntry> exportCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing((ArticleCategory c) -> String.join("\u0000", pathOf(c))))
                .map(c -> new CategoryEntry(pathOf(c), c.getSortOrder(),
                        c.getDefaultStation() == null ? null : c.getDefaultStation().getName(), c.getColor()))
                .toList();
    }

    private List<ArticleEntry> exportArticles() {
        return articleRepository.findAll().stream()
                .sorted(Comparator.comparing((Article a) -> String.join("\u0000", pathOf(a.getCategory())))
                        .thenComparing(Article::getName))
                .map(a -> new ArticleEntry(a.getName(), a.getShortName(), pathOf(a.getCategory()), a.getPrice(),
                        a.isActive(), a.getStationOverride() == null ? null : a.getStationOverride().getName(),
                        a.getModifierGroups().stream().map(ModifierGroup::getName).sorted().toList()))
                .toList();
    }

    private static String color(String value, String context) {
        try {
            return ColorCodes.normalize(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidTransferException(context + ": " + e.getMessage());
        }
    }

    private static List<String> pathOf(ArticleCategory category) {
        List<String> path = new ArrayList<>();
        for (ArticleCategory c = category; c != null; c = c.getParent()) {
            path.add(0, c.getName());
        }
        return path;
    }

    // ------------------------------------------------------------------ import

    /** Reads and checks a file; does not change any data. */
    public MasterDataDocument parse(byte[] content) {
        MasterDataDocument document;
        try {
            document = json.readValue(content, MasterDataDocument.class);
        } catch (JacksonException e) {
            throw new InvalidTransferException("Die Datei ist keine gültige JSON-Datei dieses Formats.", e);
        }
        if (document == null || !FORMAT.equals(document.format())) {
            throw new InvalidTransferException("Die Datei ist kein BonFlow-Stammdatenexport.");
        }
        if (document.version() != VERSION) {
            throw new InvalidTransferException("Nicht unterstützte Version " + document.version()
                    + " (erwartet: " + VERSION + ").");
        }
        return document;
    }

    /** The sections the file contains. */
    public Set<TransferSection> sectionsIn(MasterDataDocument document) {
        Set<TransferSection> sections = new LinkedHashSet<>();
        if (document.areas() != null) {
            sections.add(TransferSection.AREAS);
        }
        if (document.tables() != null) {
            sections.add(TransferSection.TABLES);
        }
        if (document.printers() != null) {
            sections.add(TransferSection.PRINTERS);
        }
        if (document.stations() != null) {
            sections.add(TransferSection.STATIONS);
        }
        if (document.modifierGroups() != null) {
            sections.add(TransferSection.MODIFIER_GROUPS);
        }
        if (document.categories() != null) {
            sections.add(TransferSection.CATEGORIES);
        }
        if (document.articles() != null) {
            sections.add(TransferSection.ARTICLES);
        }
        return sections;
    }

    /**
     * Imports the selected sections of the document.
     *
     * @throws InvalidTransferException if an entry is invalid or references something that does not
     *                                  exist; nothing is changed in that case
     */
    @Transactional
    public ImportResult importData(MasterDataDocument document, Set<TransferSection> sections) {
        Importer importer = new Importer();
        Map<TransferSection, ImportResult.Counts> counts = new EnumMap<>(TransferSection.class);
        // Dependencies first: printers -> stations, areas -> tables, groups, categories -> articles
        run(counts, sections, TransferSection.PRINTERS, document.printers(), importer::printers);
        run(counts, sections, TransferSection.STATIONS, document.stations(), importer::stations);
        run(counts, sections, TransferSection.AREAS, document.areas(), importer::areas);
        run(counts, sections, TransferSection.TABLES, document.tables(), importer::tables);
        run(counts, sections, TransferSection.MODIFIER_GROUPS, document.modifierGroups(), importer::groups);
        run(counts, sections, TransferSection.CATEGORIES, document.categories(), importer::categories);
        run(counts, sections, TransferSection.ARTICLES, document.articles(), importer::articles);
        return new ImportResult(counts);
    }

    private static <T> void run(Map<TransferSection, ImportResult.Counts> counts, Set<TransferSection> selected,
            TransferSection section, List<T> entries, java.util.function.Function<List<T>, ImportResult.Counts> step) {
        if (selected.contains(section)) {
            if (entries == null) {
                throw new InvalidTransferException("Die Datei enthält keine " + section.label() + ".");
            }
            counts.put(section, step.apply(entries));
        }
    }

    /** One import run: lookup tables for existing data, extended as entries are created. */
    private final class Importer {

        private final Map<String, Area> areas = new HashMap<>();
        private final Map<String, Printer> printers = new HashMap<>();
        private final Map<String, Station> stations = new HashMap<>();
        private final Map<String, ModifierGroup> groups = new HashMap<>();
        private final Map<List<String>, ArticleCategory> categories = new HashMap<>();
        private final Map<String, DiningTable> tables = new HashMap<>();
        private final Map<List<String>, Article> articles = new HashMap<>();

        Importer() {
            areaRepository.findAll().forEach(a -> areas.putIfAbsent(a.getName(), a));
            printerRepository.findAll().forEach(p -> printers.putIfAbsent(p.getName(), p));
            stationRepository.findAll().forEach(s -> stations.putIfAbsent(s.getName(), s));
            groupRepository.findAll().forEach(g -> groups.putIfAbsent(g.getName(), g));
            categoryRepository.findAll().forEach(c -> categories.putIfAbsent(pathOf(c), c));
            tableRepository.findAll().forEach(t -> tables.putIfAbsent(t.getLabel(), t));
            articleRepository.findAll().forEach(a -> articles.putIfAbsent(articleKey(pathOf(a.getCategory()), a.getName()), a));
        }

        ImportResult.Counts areas(List<AreaEntry> entries) {
            int created = 0;
            int updated = 0;
            for (AreaEntry entry : entries) {
                String name = required(entry.name(), "Bereich: Name");
                Area area = areas.get(name);
                if (area == null) {
                    area = new Area();
                    area.setName(name);
                    area.setColor(color(entry.color(), "Bereich " + name));
                    areas.put(name, areaRepository.save(area));
                    created++;
                } else {
                    if (entry.color() != null) {
                        area.setColor(color(entry.color(), "Bereich " + name));
                    }
                    updated++;
                }
            }
            return new ImportResult.Counts(created, updated);
        }

        ImportResult.Counts tables(List<TableEntry> entries) {
            int created = 0;
            int updated = 0;
            for (TableEntry entry : entries) {
                String label = required(entry.label(), "Tisch: Bezeichnung");
                Area area = areas.get(required(entry.area(), "Tisch " + label + ": Bereich"));
                if (area == null) {
                    throw missing("Bereich", entry.area(), "Tisch " + label);
                }
                DiningTable table = tables.get(label);
                boolean isNew = table == null;
                if (isNew) {
                    table = new DiningTable();
                    table.setLabel(label);
                }
                table.setArea(area);
                table.setActive(entry.active() == null || entry.active());
                if (entry.color() != null) {
                    table.setColor(color(entry.color(), "Tisch " + label));
                }
                if (isNew) {
                    tables.put(label, tableRepository.save(table));
                    created++;
                } else {
                    updated++;
                }
            }
            return new ImportResult.Counts(created, updated);
        }

        ImportResult.Counts printers(List<PrinterEntry> entries) {
            int created = 0;
            int updated = 0;
            for (PrinterEntry entry : entries) {
                String name = required(entry.name(), "Drucker: Name");
                Printer printer = printers.get(name);
                boolean isNew = printer == null;
                if (isNew) {
                    printer = new Printer();
                    printer.setName(name);
                }
                printer.setHost(required(entry.host(), "Drucker " + name + ": Host"));
                printer.setPort(entry.port() == null ? 9100 : entry.port());
                try {
                    printer.setProtocol(entry.protocol() == null ? PrinterProtocol.ESC_POS
                            : PrinterProtocol.valueOf(entry.protocol()));
                } catch (IllegalArgumentException e) {
                    throw new InvalidTransferException("Drucker " + name + ": unbekanntes Protokoll '"
                            + entry.protocol() + "'.");
                }
                printer.setPaperWidthMm(entry.paperWidthMm() == null ? 80 : entry.paperWidthMm());
                printer.setEnabled(entry.enabled() == null || entry.enabled());
                if (isNew) {
                    printers.put(name, printerRepository.save(printer));
                    created++;
                } else {
                    updated++;
                }
            }
            return new ImportResult.Counts(created, updated);
        }

        ImportResult.Counts stations(List<StationEntry> entries) {
            int created = 0;
            int updated = 0;
            for (StationEntry entry : entries) {
                String name = required(entry.name(), "Station: Name");
                Station station = stations.get(name);
                boolean isNew = station == null;
                if (isNew) {
                    station = new Station();
                    station.setName(name);
                }
                station.setHasDisplay(entry.hasDisplay() == null || entry.hasDisplay());
                Set<Printer> stationPrinters = new LinkedHashSet<>();
                for (String printerName : entry.printers() == null ? List.<String>of() : entry.printers()) {
                    Printer printer = printers.get(printerName);
                    if (printer == null) {
                        throw missing("Drucker", printerName, "Station " + name);
                    }
                    stationPrinters.add(printer);
                }
                station.setPrinters(stationPrinters);
                if (isNew) {
                    stations.put(name, stationRepository.save(station));
                    created++;
                } else {
                    updated++;
                }
            }
            return new ImportResult.Counts(created, updated);
        }

        ImportResult.Counts groups(List<ModifierGroupEntry> entries) {
            int created = 0;
            int updated = 0;
            for (ModifierGroupEntry entry : entries) {
                String name = required(entry.name(), "Modifier-Gruppe: Name");
                ModifierGroup group = groups.get(name);
                boolean isNew = group == null;
                if (isNew) {
                    group = new ModifierGroup();
                    group.setName(name);
                }
                group.setMultiSelect(entry.multiSelect() != null && entry.multiSelect());
                // Existing modifiers stay (orders may refer to them); new ones are appended.
                List<String> names = new ArrayList<>(group.getModifierNames());
                for (String modifier : entry.modifiers() == null ? List.<String>of() : entry.modifiers()) {
                    String trimmed = required(modifier, "Modifier-Gruppe " + name + ": Modifier");
                    if (!names.contains(trimmed)) {
                        names.add(trimmed);
                    }
                }
                group.setModifierNames(names);
                if (isNew) {
                    groups.put(name, groupRepository.save(group));
                    created++;
                } else {
                    updated++;
                }
            }
            return new ImportResult.Counts(created, updated);
        }

        ImportResult.Counts categories(List<CategoryEntry> entries) {
            int created = 0;
            int updated = 0;
            // parents before children
            List<CategoryEntry> sorted = entries.stream()
                    .sorted(Comparator.comparingInt(e -> e.path() == null ? 0 : e.path().size())).toList();
            for (CategoryEntry entry : sorted) {
                List<String> path = entry.path();
                if (path == null || path.isEmpty() || path.stream().anyMatch(p -> p == null || p.isBlank())) {
                    throw new InvalidTransferException("Kategorie ohne Pfad.");
                }
                String label = String.join(" > ", path);
                ArticleCategory category = categories.get(path);
                boolean isNew = category == null;
                if (isNew) {
                    category = new ArticleCategory();
                    category.setName(path.get(path.size() - 1));
                }
                if (path.size() > 1) {
                    ArticleCategory parent = categories.get(path.subList(0, path.size() - 1));
                    if (parent == null) {
                        throw missing("Übergeordnete Kategorie", String.join(" > ", path.subList(0, path.size() - 1)),
                                "Kategorie " + label);
                    }
                    category.setParent(parent);
                }
                category.setSortOrder(entry.sortOrder() == null ? 0 : entry.sortOrder());
                category.setDefaultStation(station(entry.defaultStation(), "Kategorie " + label));
                if (entry.color() != null) {
                    category.setColor(color(entry.color(), "Kategorie " + label));
                }
                if (isNew) {
                    categories.put(List.copyOf(path), categoryRepository.save(category));
                    created++;
                } else {
                    updated++;
                }
            }
            return new ImportResult.Counts(created, updated);
        }

        ImportResult.Counts articles(List<ArticleEntry> entries) {
            int created = 0;
            int updated = 0;
            for (ArticleEntry entry : entries) {
                String name = required(entry.name(), "Artikel: Name");
                if (entry.category() == null || entry.category().isEmpty()) {
                    throw new InvalidTransferException("Artikel " + name + ": Kategorie fehlt.");
                }
                ArticleCategory category = categories.get(entry.category());
                if (category == null) {
                    throw missing("Kategorie", String.join(" > ", entry.category()), "Artikel " + name);
                }
                List<String> key = articleKey(entry.category(), name);
                Article article = articles.get(key);
                boolean isNew = article == null;
                if (isNew) {
                    article = new Article();
                    article.setName(name);
                }
                article.setCategory(category);
                article.setShortName(entry.shortName());
                article.setPrice(entry.price() == null ? java.math.BigDecimal.ZERO : entry.price());
                article.setActive(entry.active() == null || entry.active());
                article.setStationOverride(station(entry.stationOverride(), "Artikel " + name));
                Set<ModifierGroup> articleGroups = new LinkedHashSet<>();
                for (String groupName : entry.modifierGroups() == null ? List.<String>of() : entry.modifierGroups()) {
                    ModifierGroup group = groups.get(groupName);
                    if (group == null) {
                        throw missing("Modifier-Gruppe", groupName, "Artikel " + name);
                    }
                    articleGroups.add(group);
                }
                article.setModifierGroups(articleGroups);
                if (isNew) {
                    articles.put(key, articleRepository.save(article));
                    created++;
                } else {
                    updated++;
                }
            }
            return new ImportResult.Counts(created, updated);
        }

        private Station station(String name, String context) {
            if (name == null || name.isBlank()) {
                return null;
            }
            Station station = stations.get(name);
            if (station == null) {
                throw missing("Station", name, context);
            }
            return station;
        }
    }

    private static List<String> articleKey(List<String> categoryPath, String name) {
        List<String> key = new ArrayList<>(categoryPath);
        key.add(name);
        return key;
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransferException(what + " fehlt.");
        }
        return value.trim();
    }

    private static InvalidTransferException missing(String kind, String name, String context) {
        return new InvalidTransferException(context + ": " + kind + " '" + name
                + "' existiert nicht. Bitte zuerst importieren oder anlegen.");
    }
}
