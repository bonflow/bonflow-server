package ch.bonflow.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import ch.bonflow.TestcontainersConfiguration;
import ch.bonflow.masterdata.Area;
import ch.bonflow.masterdata.AreaRepository;
import ch.bonflow.masterdata.Article;
import ch.bonflow.masterdata.ArticleCategory;
import ch.bonflow.masterdata.ArticleCategoryRepository;
import ch.bonflow.masterdata.ArticleRepository;
import ch.bonflow.masterdata.DiningTable;
import ch.bonflow.masterdata.DiningTableRepository;
import ch.bonflow.masterdata.ModifierGroup;
import ch.bonflow.masterdata.ModifierGroupRepository;
import ch.bonflow.masterdata.Printer;
import ch.bonflow.masterdata.PrinterRepository;
import ch.bonflow.masterdata.Station;
import ch.bonflow.masterdata.StationRepository;
import ch.bonflow.ordering.AddItems;
import ch.bonflow.ordering.NewItem;
import ch.bonflow.ordering.OrderService;
import ch.bonflow.ordering.OrderView;
import ch.bonflow.ordering.SubmitOrder;
import ch.bonflow.routing.TicketKind;
import ch.bonflow.routing.TicketStatus;
import ch.bonflow.users.AppUser;
import ch.bonflow.users.WaiterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Station board and push notifications. Not transactional: the push is sent after commit. The
 * tables are emptied after each test.
 */
@SpringBootTest(properties = {
        "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123",
        "bonflow.printing.poll-interval=1h"
})
@Import(TestcontainersConfiguration.class)
class DisplayTests {

    @Autowired
    OrderService orders;
    @Autowired
    DisplayService display;
    @Autowired
    StationBroadcaster broadcaster;
    @Autowired
    WaiterService waiters;
    @Autowired
    AreaRepository areas;
    @Autowired
    DiningTableRepository tables;
    @Autowired
    StationRepository stations;
    @Autowired
    PrinterRepository printers;
    @Autowired
    ArticleCategoryRepository categories;
    @Autowired
    ModifierGroupRepository modifierGroups;
    @Autowired
    ArticleRepository articles;
    @Autowired
    JdbcTemplate jdbc;

    long waiterId;
    long tableId;
    long kitchenId;
    long barId;
    Article schnitzel;
    Article beer;

    @BeforeEach
    void data() {
        AppUser anna = new AppUser();
        anna.setDisplayName("Anna");
        anna.setLogin("anna");
        waiterId = waiters.save(anna, "1234").getId();

        Area hall = new Area();
        hall.setName("Saal");
        areas.save(hall);
        DiningTable table = new DiningTable();
        table.setArea(hall);
        table.setLabel("T12");
        tableId = tables.save(table).getId();

        Printer printer = new Printer();
        printer.setName("Bar");
        printer.setHost("127.0.0.1");
        printer.setPort(1);
        printers.save(printer);
        Station kitchen = station("Küche", null);
        Station bar = station("Bar", printer);
        kitchenId = kitchen.getId();
        barId = bar.getId();

        ModifierGroup cooking = new ModifierGroup();
        cooking.setName("Garstufe");
        cooking.setModifierNames(List.of("rare", "medium"));
        modifierGroups.save(cooking);

        schnitzel = article("Schnitzel", kitchen);
        schnitzel.setModifierGroups(Set.of(cooking));
        beer = article("Bier", bar);
        articles.saveAll(List.of(schnitzel, beer));
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE TABLE print_job, station_ticket, customer_order, article, article_category, "
                + "dining_table, area, station_printer, station, printer, modifier_group, app_user CASCADE");
        jdbc.execute("TRUNCATE TABLE event_publication");
    }

    @Test
    void boardShowsOpenTicketsAndSupportsBumpAndRecall() {
        long medium = modifierGroups.findAll().get(0).getModifiers().get(1).getId();
        OrderView order = orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, "Allergie",
                List.of(new NewItem(schnitzel.getId(), 2, "ohne Salz", 1, Set.of(medium)),
                        new NewItem(beer.getId(), 1, null, null, null))), waiterId).order();
        orders.addItems(order.id(), new AddItems(2, List.of(new NewItem(schnitzel.getId(), 1, null, null, null))));

        StationBoard kitchen = display.board(kitchenId);
        assertThat(kitchen.stationName()).isEqualTo("Küche");
        assertThat(kitchen.open()).hasSize(2);
        TicketCard first = kitchen.open().get(0);
        assertThat(first.kind()).isEqualTo(TicketKind.NEW);
        assertThat(first.tableLabel()).isEqualTo("T12");
        assertThat(first.waiterName()).isEqualTo("Anna");
        assertThat(first.orderNote()).isEqualTo("Allergie");
        assertThat(first.lines()).singleElement().satisfies(l -> {
            assertThat(l.quantity()).isEqualTo(2);
            assertThat(l.name()).isEqualTo("Schnitzel");
            assertThat(l.modifiers()).containsExactly("medium");
            assertThat(l.note()).isEqualTo("ohne Salz");
        });
        assertThat(kitchen.open().get(1).kind()).isEqualTo(TicketKind.ADDITION);
        // the bar only sees its own ticket
        assertThat(display.board(barId).open()).singleElement()
                .satisfies(c -> assertThat(c.lines().get(0).name()).isEqualTo("Bier"));

        display.advance(first.ticketId());
        assertThat(display.board(kitchenId).open().get(0).status()).isEqualTo(TicketStatus.IN_PROGRESS);
        display.advance(first.ticketId());
        StationBoard afterDone = display.board(kitchenId);
        assertThat(afterDone.open()).hasSize(1);
        assertThat(afterDone.recentlyDone()).extracting(TicketCard::ticketId).containsExactly(first.ticketId());

        display.recall(first.ticketId());
        StationBoard recalled = display.board(kitchenId);
        assertThat(recalled.open()).hasSize(2);
        assertThat(recalled.recentlyDone()).isEmpty();
    }

    @Test
    void recentlyDoneIsOrderedByCompletionTimeNotByTicketId() throws Exception {
        long first = orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                List.of(new NewItem(schnitzel.getId(), 1, null, null, null))), waiterId).order().tickets().get(0).id();
        long second = orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                List.of(new NewItem(schnitzel.getId(), 2, null, null, null))), waiterId).order().tickets().get(0).id();

        // the later ticket is finished first, the earlier one afterwards
        display.advance(second);
        display.advance(second);
        Thread.sleep(20);
        display.advance(first);
        display.advance(first);

        assertThat(display.board(kitchenId).recentlyDone()).extracting(TicketCard::ticketId)
                .containsExactly(first, second);
    }

    @Test
    void orderDetailResolvesNamesAndDeliveryState() {
        OrderView order = orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, "Geburtstag",
                List.of(new NewItem(schnitzel.getId(), 2, "scharf", 1, null),
                        new NewItem(beer.getId(), 1, null, null, null))), waiterId).order();
        display.advance(order.tickets().get(0).id());
        display.advance(order.tickets().get(0).id());

        OrderDetail detail = display.orderDetail(order.id());
        assertThat(detail.tableLabel()).isEqualTo("T12");
        assertThat(detail.waiterName()).isEqualTo("Anna");
        assertThat(detail.items()).extracting(OrderDetail.Item::name).containsExactly("Schnitzel", "Bier");
        assertThat(detail.tickets()).extracting(OrderDetail.Ticket::stationName).containsExactly("Küche", "Bar");
        assertThat(detail.tickets().get(0).doneAt()).isNotNull();
        assertThat(detail.tickets().get(1).doneAt()).isNull();
    }

    @Test
    void cancelledItemsAreMarkedAndStornoTicketAppears() {
        OrderView order = orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                List.of(new NewItem(schnitzel.getId(), 1, null, null, null),
                        new NewItem(beer.getId(), 1, null, null, null))), waiterId).order();
        long schnitzelItem = order.items().get(0).id();
        orders.cancelItems(order.id(), Set.of(schnitzelItem));

        List<TicketCard> cards = display.board(kitchenId).open();
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).kind()).isEqualTo(TicketKind.NEW);
        assertThat(cards.get(0).lines().get(0).cancelled()).isTrue();
        assertThat(cards.get(1).kind()).isEqualTo(TicketKind.STORNO);
    }

    @Test
    void stationViewsAreNotifiedAfterCommit() {
        AtomicInteger kitchenPings = new AtomicInteger();
        AtomicInteger barPings = new AtomicInteger();
        Runnable unregisterKitchen = broadcaster.register(kitchenId, kitchenPings::incrementAndGet);
        Runnable unregisterBar = broadcaster.register(barId, barPings::incrementAndGet);
        try {
            OrderView order = orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                    List.of(new NewItem(schnitzel.getId(), 1, null, null, null))), waiterId).order();
            assertThat(kitchenPings.get()).isEqualTo(1);
            assertThat(barPings.get()).isZero();

            display.advance(order.tickets().get(0).id());
            assertThat(kitchenPings.get()).isEqualTo(2);

            unregisterKitchen.run();
            display.advance(order.tickets().get(0).id());
            assertThat(kitchenPings.get()).isEqualTo(2);
            assertThat(broadcaster.listenerCount(kitchenId)).isZero();
        } finally {
            unregisterKitchen.run();
            unregisterBar.run();
        }
    }

    @Test
    void ticketWithUndeliveredPrintJobIsFlagged() {
        orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                List.of(new NewItem(beer.getId(), 1, null, null, null))), waiterId);
        // the print listener queues the job asynchronously after commit
        await().atMost(Duration.ofSeconds(15))
                .until(() -> jdbc.queryForObject("select count(*) from print_job", Long.class) == 1);
        assertThat(display.board(barId).open().get(0).printProblem()).isFalse();

        jdbc.update("update print_job set attempts = 2, last_error = 'Connection refused'");
        assertThat(display.board(barId).open().get(0).printProblem()).isTrue();
    }

    private Station station(String name, Printer printer) {
        Station station = new Station();
        station.setName(name);
        if (printer != null) {
            station.setPrinters(Set.of(printer));
        }
        return stations.save(station);
    }

    private Article article(String name, Station station) {
        ArticleCategory category = new ArticleCategory();
        category.setName("Kat " + name);
        category.setDefaultStation(station);
        categories.save(category);
        Article article = new Article();
        article.setName(name);
        article.setCategory(category);
        article.setPrice(new BigDecimal("10.00"));
        return article;
    }
}
