package ch.famfrehner.bonflow.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ch.famfrehner.bonflow.TestcontainersConfiguration;
import ch.famfrehner.bonflow.masterdata.Area;
import ch.famfrehner.bonflow.masterdata.AreaRepository;
import ch.famfrehner.bonflow.masterdata.Article;
import ch.famfrehner.bonflow.masterdata.ArticleCategory;
import ch.famfrehner.bonflow.masterdata.ArticleCategoryRepository;
import ch.famfrehner.bonflow.masterdata.ArticleRepository;
import ch.famfrehner.bonflow.masterdata.DiningTable;
import ch.famfrehner.bonflow.masterdata.DiningTableRepository;
import ch.famfrehner.bonflow.masterdata.Printer;
import ch.famfrehner.bonflow.masterdata.PrinterRepository;
import ch.famfrehner.bonflow.masterdata.Station;
import ch.famfrehner.bonflow.masterdata.StationRepository;
import ch.famfrehner.bonflow.ordering.NewItem;
import ch.famfrehner.bonflow.ordering.OrderService;
import ch.famfrehner.bonflow.ordering.OrderView;
import ch.famfrehner.bonflow.ordering.SubmitOrder;
import ch.famfrehner.bonflow.users.AppUser;
import ch.famfrehner.bonflow.users.WaiterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Print jobs against a simulated printer, including the outbox path from {@code OrderService}
 * through the event publication registry. Not transactional on purpose: the listener only runs
 * after the order transaction committed. The tables are emptied again after each test.
 */
@SpringBootTest(properties = {
        "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123",
        "bonflow.printing.poll-interval=1h" // the tests trigger dispatching themselves
})
@Import(TestcontainersConfiguration.class)
class PrintingTests {

    private static final Charset CP858 = Charset.forName("Cp858");

    @Autowired
    OrderService orders;
    @Autowired
    PrintDispatcher dispatcher;
    @Autowired
    PrintJobRepository jobs;
    @Autowired
    PrintAdminService printAdmin;
    @Autowired
    WaiterService waiters;
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
    ArticleRepository articles;
    @Autowired
    JdbcTemplate jdbc;

    long waiterId;
    long tableId;
    Article beer;
    Printer barPrinter;

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
        table.setLabel("T7");
        tableId = tables.save(table).getId();

        barPrinter = new Printer();
        barPrinter.setName("Bar");
        barPrinter.setHost("127.0.0.1");
        printers.save(barPrinter);
        Station bar = new Station();
        bar.setName("Bar");
        bar.setPrinters(Set.of(barPrinter));
        stations.save(bar);
        ArticleCategory drinks = new ArticleCategory();
        drinks.setName("Getränke");
        drinks.setDefaultStation(bar);
        categories.save(drinks);
        beer = new Article();
        beer.setName("Bier");
        beer.setCategory(drinks);
        beer.setPrice(new BigDecimal("5.50"));
        articles.save(beer);
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE TABLE print_job, station_ticket, customer_order, article, article_category, "
                + "dining_table, area, station_printer, station, printer, app_user CASCADE");
        jdbc.execute("TRUNCATE TABLE event_publication");
    }

    @Test
    void submittedOrderIsPrintedViaTheOutbox() throws Exception {
        try (FakePrinter printer = FakePrinter.start()) {
            point(barPrinter, printer.port());

            OrderView order = orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                    List.of(new NewItem(beer.getId(), 3, "ohne Schaum", null, null))), waiterId).order();

            // the listener runs asynchronously after commit and queues the job
            await().atMost(Duration.ofSeconds(15)).until(() -> jobs.count() == 1);
            dispatcher.dispatchDue();

            await().atMost(Duration.ofSeconds(5)).until(() -> printer.received().size() == 1);
            assertThat(printer.received()).singleElement().satisfies(bytes -> {
                String text = new String(bytes, CP858);
                assertThat(text).contains("BESTELLUNG", "Tisch T7", "Anna", "3x Bier", "ohne Schaum");
            });
            assertThat(jobs.findAll()).singleElement()
                    .satisfies(job -> assertThat(job.getStatus()).isEqualTo(PrintJobStatus.SENT));
            // the ticket remembers that it was printed
            assertThat(orders.get(order.id()).tickets()).singleElement()
                    .satisfies(t -> assertThat(t.printedAt()).isNotNull());
        }
    }

    @Test
    void jobStaysQueuedWhilePrinterIsOfflineAndIsSentWhenItReturns() throws Exception {
        int port = FakePrinter.freePort();
        point(barPrinter, port);

        orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                List.of(new NewItem(beer.getId(), 1, null, null, null))), waiterId);
        await().atMost(Duration.ofSeconds(15)).until(() -> jobs.count() == 1);

        dispatcher.dispatchDue(); // printer is off
        assertThat(jobs.findAll()).singleElement().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PENDING);
            assertThat(job.getAttempts()).isEqualTo(1);
            assertThat(job.getLastError()).isNotBlank();
        });
        // not due again yet: no new attempt without waiting for the backoff
        dispatcher.dispatchDue();
        assertThat(jobs.findAll().get(0).getAttempts()).isEqualTo(1);

        try (FakePrinter printer = FakePrinter.start(port)) {
            jdbc.update("update print_job set next_attempt_at = now() - interval '1 second'");
            dispatcher.dispatchDue();
            await().atMost(Duration.ofSeconds(5)).until(() -> printer.received().size() == 1);
            assertThat(jobs.findAll()).singleElement()
                    .satisfies(job -> assertThat(job.getStatus()).isEqualTo(PrintJobStatus.SENT));
        }
    }

    @Test
    void disabledPrintersGetNoJobs() throws Exception {
        barPrinter.setEnabled(false);
        printers.save(barPrinter);

        orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                List.of(new NewItem(beer.getId(), 1, null, null, null))), waiterId);

        // give the listener time to run, then verify nothing was queued
        Thread.sleep(1500);
        assertThat(jobs.count()).isZero();
    }

    @Test
    void testPrintReportsSuccessAndFailure() throws Exception {
        try (FakePrinter printer = FakePrinter.start()) {
            point(barPrinter, printer.port());
            TestPrintResult ok = printAdmin.testPrint(barPrinter.getId());
            assertThat(ok.success()).isTrue();
            await().atMost(Duration.ofSeconds(5)).until(() -> printer.received().size() == 1);
            assertThat(new String(printer.received().get(0), CP858)).contains("TESTDRUCK", "Bar");
        }
        point(barPrinter, FakePrinter.freePort());
        TestPrintResult failed = printAdmin.testPrint(barPrinter.getId());
        assertThat(failed.success()).isFalse();
        assertThat(failed.message()).contains("nicht erreichbar");
    }

    @Test
    void statusShowsOfflinePrinterAndRetryMakesJobsDue() throws Exception {
        point(barPrinter, FakePrinter.freePort());
        assertThat(printAdmin.printerStatuses()).singleElement().satisfies(s -> {
            assertThat(s.offline()).isFalse();
            assertThat(s.pendingJobs()).isZero();
        });

        orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                List.of(new NewItem(beer.getId(), 1, null, null, null))), waiterId);
        await().atMost(Duration.ofSeconds(15)).until(() -> jobs.count() == 1);
        dispatcher.dispatchDue(); // fails: nothing listens

        PrinterStatus status = printAdmin.printerStatuses().get(0);
        assertThat(status.offline()).isTrue();
        assertThat(status.pendingJobs()).isEqualTo(1);
        assertThat(status.failingJobs()).isEqualTo(1);
        assertThat(status.lastError()).isNotBlank();
        assertThat(status.waitingSince()).isNotNull();
        assertThat(printAdmin.anyPrinterOffline()).isTrue();

        // backed off: not due until retryNow
        dispatcher.dispatchDue();
        assertThat(jobs.findAll().get(0).getAttempts()).isEqualTo(1);
        assertThat(printAdmin.retryNow(barPrinter.getId())).isEqualTo(1);
        dispatcher.dispatchDue();
        assertThat(jobs.findAll().get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    void reprintQueuesANewJobMarkedAsReprint() throws Exception {
        try (FakePrinter printer = FakePrinter.start()) {
            point(barPrinter, printer.port());
            OrderView order = orders.submit(new SubmitOrder(UUID.randomUUID(), tableId, null,
                    List.of(new NewItem(beer.getId(), 1, null, null, null))), waiterId).order();
            await().atMost(Duration.ofSeconds(15)).until(() -> jobs.count() == 1);
            dispatcher.dispatchDue();
            await().atMost(Duration.ofSeconds(5)).until(() -> printer.received().size() == 1);

            assertThat(printAdmin.reprintTicket(order.tickets().get(0).id())).isEqualTo(1);
            assertThat(jobs.count()).isEqualTo(2);
            dispatcher.dispatchDue();
            await().atMost(Duration.ofSeconds(5)).until(() -> printer.received().size() == 2);
            assertThat(new String(printer.received().get(0), CP858)).doesNotContain("NACHDRUCK");
            assertThat(new String(printer.received().get(1), CP858)).contains("NACHDRUCK", "Bier");
        }
    }

    private void point(Printer printer, int port) {
        printer.setPort(port);
        printers.save(printer);
    }
}
