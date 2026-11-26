package ch.bonflow.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import ch.bonflow.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123")
@Import(TestcontainersConfiguration.class)
@Transactional
class MasterDataTransferTests {

    private static final Set<TransferSection> ALL = EnumSet.allOf(TransferSection.class);

    @PersistenceContext
    EntityManager em;

    @Autowired
    MasterDataTransferService transfer;
    @Autowired
    AreaRepository areas;
    @Autowired
    DiningTableRepository tables;
    @Autowired
    PrinterRepository printers;
    @Autowired
    StationRepository stations;
    @Autowired
    ArticleCategoryRepository categories;
    @Autowired
    ModifierGroupRepository groups;
    @Autowired
    ArticleRepository articles;

    @Test
    void exportedDataComesBackAfterItWasDeleted() {
        Fixture fixture = createData();
        byte[] file = transfer.export(ALL);
        String text = new String(file, StandardCharsets.UTF_8);
        assertThat(text).contains("bonflow-masterdata", "T-Burger Classic", "T-Terrasse");

        // delete everything the test created, children first
        articles.delete(fixture.article());
        categories.delete(fixture.child());
        categories.delete(fixture.parent());
        groups.delete(fixture.group());
        tables.delete(fixture.table());
        areas.delete(fixture.area());
        stations.delete(fixture.station());
        printers.delete(fixture.printer());
        flushAndClear();
        assertThat(findArticle()).isNull();

        ImportResult result = transfer.importData(transfer.parse(file), ALL);

        assertThat(result.counts().get(TransferSection.ARTICLES).created()).isGreaterThanOrEqualTo(1);
        Article article = findArticle();
        assertThat(article).isNotNull();
        assertThat(article.getPrice()).isEqualByComparingTo("18.50");
        assertThat(article.getCategory().getName()).isEqualTo("T-Burger");
        assertThat(article.getCategory().getParent().getName()).isEqualTo("T-Essen");
        assertThat(article.getCategory().getDefaultStation().getName()).isEqualTo("T-Küche");
        assertThat(article.getCategory().getDefaultStation().getPrinters()).extracting(Printer::getName)
                .containsExactly("T-Drucker");
        assertThat(article.getModifierGroups()).extracting(ModifierGroup::getName).containsExactly("T-Garstufe");
        assertThat(article.getModifierGroups().iterator().next().getModifierNames())
                .containsExactly("roh", "medium");
        assertThat(tables.findAll()).extracting(DiningTable::getLabel).contains("T-1");
    }

    @Test
    void colorsSurviveExportImportAndInvalidOnesAreRejected() {
        Fixture fixture = createData();
        Area area = areas.findById(fixture.area().getId()).orElseThrow();
        area.setColor("#1e88e5");
        categories.findById(fixture.parent().getId()).orElseThrow().setColor("#E53935");
        em.flush();
        byte[] file = transfer.export(ALL);
        assertThat(new String(file, StandardCharsets.UTF_8)).contains("#1E88E5", "#E53935");

        area.setColor(null);
        flushAndClear();
        transfer.importData(transfer.parse(file), ALL);
        flushAndClear();
        assertThat(areas.findAll()).filteredOn(a -> a.getName().equals("T-Terrasse"))
                .extracting(Area::getColor).containsExactly("#1E88E5");

        String bad = new String(file, StandardCharsets.UTF_8).replace("#1E88E5", "blau");
        assertThatThrownBy(() -> transfer.importData(transfer.parse(bad.getBytes(StandardCharsets.UTF_8)), ALL))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void importingTwiceUpdatesInsteadOfDuplicating() {
        createData();
        byte[] file = transfer.export(ALL);
        long articleCount = articles.count();

        ImportResult result = transfer.importData(transfer.parse(file), ALL);

        assertThat(articles.count()).isEqualTo(articleCount);
        assertThat(result.counts().get(TransferSection.ARTICLES).created()).isZero();
        assertThat(result.counts().get(TransferSection.ARTICLES).updated()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void importsOnlyTheSelectedSections() {
        createData();
        byte[] file = transfer.export(ALL);

        ImportResult result = transfer.importData(transfer.parse(file), Set.of(TransferSection.AREAS));

        assertThat(result.counts()).containsOnlyKeys(TransferSection.AREAS);
    }

    @Test
    void rejectsReferencesToMissingData() {
        String json = """
                {"format":"bonflow-masterdata","version":1,
                 "tables":[{"label":"T-X","area":"T-Gibt-es-nicht"}]}""";

        MasterDataDocument document = transfer.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> transfer.importData(document, Set.of(TransferSection.TABLES)))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("T-Gibt-es-nicht");
    }

    @Test
    void rejectsFilesOfAnotherFormat() {
        assertThatThrownBy(() -> transfer.parse("{\"hello\":1}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidTransferException.class);
        assertThatThrownBy(() -> transfer.parse("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidTransferException.class);
    }

    private Article findArticle() {
        return articles.findAll().stream().filter(a -> a.getName().equals("T-Burger Classic")).findFirst()
                .orElse(null);
    }

    private Fixture createData() {
        Printer printer = new Printer();
        printer.setName("T-Drucker");
        printer.setHost("192.168.1.50");
        printers.save(printer);

        Station station = new Station();
        station.setName("T-Küche");
        station.setPrinters(Set.of(printer));
        stations.save(station);

        Area area = new Area();
        area.setName("T-Terrasse");
        areas.save(area);
        DiningTable table = new DiningTable();
        table.setArea(area);
        table.setLabel("T-1");
        tables.save(table);

        ModifierGroup group = new ModifierGroup();
        group.setName("T-Garstufe");
        group.setModifierNames(List.of("roh", "medium"));
        groups.save(group);

        ArticleCategory parent = new ArticleCategory();
        parent.setName("T-Essen");
        categories.save(parent);
        ArticleCategory child = new ArticleCategory();
        child.setName("T-Burger");
        child.setParent(parent);
        child.setDefaultStation(station);
        categories.save(child);

        Article article = new Article();
        article.setName("T-Burger Classic");
        article.setCategory(child);
        article.setPrice(new BigDecimal("18.50"));
        article.setModifierGroups(Set.of(group));
        articles.save(article);
        flushAndClear();
        return new Fixture(printer, station, area, table, group, parent, child, article);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private record Fixture(Printer printer, Station station, Area area, DiningTable table, ModifierGroup group,
            ArticleCategory parent, ArticleCategory child, Article article) {
    }
}
