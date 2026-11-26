package ch.bonflow.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

/** Checks the JPA mapping against the Flyway schema and the station routing rule. */
@SpringBootTest(properties = "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123")
@Import(TestcontainersConfiguration.class)
@Transactional
class MasterDataPersistenceTests {

    @PersistenceContext
    EntityManager em;

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
    ModifierGroupRepository modifierGroups;
    @Autowired
    ArticleRepository articles;

    @Test
    void storesTablesAndReloadsThem() {
        Area terrace = new Area();
        terrace.setName("Terrace");
        areas.save(terrace);
        DiningTable table = new DiningTable();
        table.setArea(terrace);
        table.setLabel("T12");
        Long id = tables.save(table).getId();
        flushAndClear();

        DiningTable loaded = tables.findById(id).orElseThrow();
        assertThat(loaded.getLabel()).isEqualTo("T12");
        assertThat(loaded.getArea().getName()).isEqualTo("Terrace");
        assertThat(loaded.isActive()).isTrue();
    }

    @Test
    void articleStationFollowsOverrideThenCategoryDefault() {
        Printer printer = new Printer();
        printer.setName("Bar printer");
        printer.setHost("10.0.0.20");
        printers.save(printer);

        Station kitchen = station("Kitchen");
        Station bar = station("Bar");
        bar.setPrinters(Set.of(printer));
        stations.save(bar);

        ArticleCategory mains = category("Mains", kitchen);
        ModifierGroup cooking = new ModifierGroup();
        cooking.setName("Cooking");
        cooking.setModifierNames(List.of("rare", "medium", "well done"));
        modifierGroups.save(cooking);

        Article steak = article("Steak", mains);
        steak.setPrice(new BigDecimal("34.50"));
        steak.setModifierGroups(Set.of(cooking));
        Article pairedWine = article("Wine pairing", mains);
        pairedWine.setStationOverride(bar);
        articles.saveAll(List.of(steak, pairedWine));
        flushAndClear();

        Article loadedSteak = articles.findById(steak.getId()).orElseThrow();
        Article loadedWine = articles.findById(pairedWine.getId()).orElseThrow();
        assertThat(loadedSteak.resolveStation().getName()).isEqualTo("Kitchen");
        assertThat(loadedWine.resolveStation().getName()).isEqualTo("Bar");
        assertThat(loadedWine.resolveStation().getPrinters()).extracting(Printer::getHost)
                .containsExactly("10.0.0.20");
        assertThat(loadedSteak.getPrice()).isEqualByComparingTo("34.50");
        assertThat(loadedSteak.getModifierGroups()).singleElement()
                .satisfies(group -> assertThat(group.getModifierNames())
                        .containsExactly("rare", "medium", "well done"));
    }

    @Test
    void modifierNamesKeepIdsWhenOnlyRenamed() {
        ModifierGroup group = new ModifierGroup();
        group.setName("Extras");
        group.setModifierNames(List.of("cheese", "bacon"));
        modifierGroups.save(group);
        flushAndClear();

        ModifierGroup loaded = modifierGroups.findById(group.getId()).orElseThrow();
        loaded.setModifierNames(List.of("extra cheese"));
        modifierGroups.save(loaded);
        flushAndClear();

        assertThat(modifierGroups.findById(group.getId()).orElseThrow().getModifierNames())
                .containsExactly("extra cheese");
    }

    private Station station(String name) {
        Station station = new Station();
        station.setName(name);
        return stations.save(station);
    }

    private ArticleCategory category(String name, Station defaultStation) {
        ArticleCategory category = new ArticleCategory();
        category.setName(name);
        category.setDefaultStation(defaultStation);
        return categories.save(category);
    }

    private Article article(String name, ArticleCategory category) {
        Article article = new Article();
        article.setName(name);
        article.setCategory(category);
        return article;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
