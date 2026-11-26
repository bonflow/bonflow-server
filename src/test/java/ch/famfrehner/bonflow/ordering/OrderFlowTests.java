package ch.famfrehner.bonflow.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import ch.famfrehner.bonflow.masterdata.ModifierGroup;
import ch.famfrehner.bonflow.masterdata.ModifierGroupRepository;
import ch.famfrehner.bonflow.masterdata.Station;
import ch.famfrehner.bonflow.masterdata.StationRepository;
import ch.famfrehner.bonflow.routing.TicketKind;
import ch.famfrehner.bonflow.routing.TicketService;
import ch.famfrehner.bonflow.routing.TicketStatus;
import ch.famfrehner.bonflow.routing.TicketSummary;
import ch.famfrehner.bonflow.users.AppUser;
import ch.famfrehner.bonflow.users.WaiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Order submission, routing into station tickets, follow-ups, storno and the REST surface. */
@SpringBootTest(properties = "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, OrderFlowTests.CollectorConfig.class})
@Transactional
class OrderFlowTests {

    static class EventCollector {
        final List<OrderSubmitted> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @EventListener
        void on(OrderSubmitted event) {
            events.add(event);
        }
    }

    @TestConfiguration
    static class CollectorConfig {
        @Bean
        EventCollector eventCollector() {
            return new EventCollector();
        }
    }

    @Autowired
    OrderService orders;
    @Autowired
    TicketService tickets;
    @Autowired
    WaiterService waiters;
    @Autowired
    MockMvc mvc;
    @Autowired
    AreaRepository areas;
    @Autowired
    DiningTableRepository tables;
    @Autowired
    StationRepository stations;
    @Autowired
    ArticleCategoryRepository categories;
    @Autowired
    ModifierGroupRepository modifierGroups;
    @Autowired
    ArticleRepository articles;
    @Autowired
    EventCollector collector;

    long waiterId;
    long tableId;
    long kitchenId;
    long barId;
    Article schnitzel;
    Article beer;
    Article noStation;
    ModifierGroup cooking;
    ModifierGroup extras;

    @BeforeEach
    void data() {
        collector.events.clear();
        AppUser anna = new AppUser();
        anna.setDisplayName("Anna");
        anna.setLogin("anna");
        waiterId = waiters.save(anna, "1234").getId();

        Area hall = new Area();
        hall.setName("Saal");
        areas.save(hall);
        DiningTable table = new DiningTable();
        table.setArea(hall);
        table.setLabel("T1");
        tableId = tables.save(table).getId();

        Station kitchen = station("Küche");
        Station bar = station("Bar");
        kitchenId = kitchen.getId();
        barId = bar.getId();

        cooking = new ModifierGroup();
        cooking.setName("Garstufe");
        cooking.setModifierNames(List.of("rare", "medium"));
        modifierGroups.save(cooking);
        extras = new ModifierGroup();
        extras.setName("Extras");
        extras.setMultiSelect(true);
        extras.setModifierNames(List.of("Käse", "Speck"));
        modifierGroups.save(extras);

        schnitzel = article("Schnitzel", category("Hauptgang", kitchen));
        schnitzel.setModifierGroups(Set.of(cooking, extras));
        beer = article("Bier", category("Getränke", bar));
        noStation = article("Rätsel", category("Ohne Station", null));
        articles.saveAll(List.of(schnitzel, beer, noStation));
    }

    @Test
    void submitSplitsItemsIntoOneTicketPerStation() {
        OrderResult result = orders.submit(order(UUID.randomUUID(), line(schnitzel, 2), line(beer, 1),
                line(beer, 3)), waiterId);

        assertThat(result.created()).isTrue();
        OrderView view = result.order();
        assertThat(view.status()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(view.items()).hasSize(3);
        assertThat(view.tickets()).hasSize(2).allSatisfy(t -> {
            assertThat(t.kind()).isEqualTo(TicketKind.NEW);
            assertThat(t.status()).isEqualTo(TicketStatus.NEW);
            assertThat(t.submissionNo()).isEqualTo(1);
        });
        assertThat(ticketOf(view, kitchenId).itemIds()).hasSize(1);
        assertThat(ticketOf(view, barId).itemIds()).hasSize(2);
        assertThat(collector.events).singleElement().satisfies(e -> {
            assertThat(e.orderId()).isEqualTo(view.id());
            assertThat(e.kind()).isEqualTo(TicketKind.NEW);
            assertThat(e.ticketIds()).hasSize(2);
        });
    }

    @Test
    void submittingTheSameIdTwiceCreatesNothingNew() {
        UUID id = UUID.randomUUID();
        SubmitOrder cmd = order(id, line(schnitzel, 1), line(beer, 1));
        orders.submit(cmd, waiterId);
        OrderResult again = orders.submit(cmd, waiterId);

        assertThat(again.created()).isFalse();
        assertThat(again.order().tickets()).hasSize(2);
        assertThat(again.order().items()).hasSize(2);
        assertThat(collector.events).hasSize(1);
    }

    @Test
    void sameIdFromAnotherWaiterIsAConflict() {
        UUID id = UUID.randomUUID();
        orders.submit(order(id, line(beer, 1)), waiterId);
        assertThatThrownBy(() -> orders.submit(order(id, line(beer, 1)), waiterId + 1000))
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    void followUpCreatesAdditionTicketsOnly() {
        UUID id = UUID.randomUUID();
        OrderView first = orders.submit(order(id, line(schnitzel, 1)), waiterId).order();

        OrderView second = orders.addItems(id, new AddItems(2, List.of(line(beer, 2))));

        assertThat(second.items()).hasSize(2);
        assertThat(second.tickets()).hasSize(2);
        TicketSummary addition = ticketOf(second, barId);
        assertThat(addition.kind()).isEqualTo(TicketKind.ADDITION);
        assertThat(addition.submissionNo()).isEqualTo(2);
        // the already handed over ticket is untouched
        assertThat(ticketOf(second, kitchenId).id()).isEqualTo(ticketOf(first, kitchenId).id());
        assertThat(collector.events).extracting(OrderSubmitted::kind)
                .containsExactly(TicketKind.NEW, TicketKind.ADDITION);
    }

    @Test
    void followUpWithStaleSubmissionNumberIsRejected() {
        UUID id = UUID.randomUUID();
        orders.submit(order(id, line(beer, 1)), waiterId);
        orders.addItems(id, new AddItems(2, List.of(line(beer, 1))));
        // retry of the same request
        assertThatThrownBy(() -> orders.addItems(id, new AddItems(2, List.of(line(beer, 1)))))
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    void cancellingItemsCreatesStornoTicketAtTheirStation() {
        UUID id = UUID.randomUUID();
        OrderView view = orders.submit(order(id, line(schnitzel, 1), line(beer, 1)), waiterId).order();
        long beerItem = ticketOf(view, barId).itemIds().get(0);

        OrderView after = orders.cancelItems(id, Set.of(beerItem));

        assertThat(after.status()).isEqualTo(OrderStatus.SUBMITTED);
        assertThat(after.items()).filteredOn(i -> i.id() == beerItem).singleElement()
                .satisfies(i -> assertThat(i.status()).isEqualTo(ItemStatus.CANCELLED));
        List<TicketSummary> storno = after.tickets().stream().filter(t -> t.kind() == TicketKind.STORNO).toList();
        assertThat(storno).singleElement().satisfies(t -> {
            assertThat(t.stationId()).isEqualTo(barId);
            assertThat(t.itemIds()).containsExactly(beerItem);
        });
        assertThatThrownBy(() -> orders.cancelItems(id, Set.of(beerItem)))
                .isInstanceOf(OrderRejectedException.class);
    }

    @Test
    void cancellingEverythingCancelsTheOrder() {
        UUID id = UUID.randomUUID();
        OrderView view = orders.submit(order(id, line(beer, 1)), waiterId).order();
        OrderView after = orders.cancelItems(id, Set.copyOf(ticketOf(view, barId).itemIds()));
        assertThat(after.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orders.addItems(id, new AddItems(2, List.of(line(beer, 1)))))
                .isInstanceOf(OrderRejectedException.class);
    }

    @Test
    void completedOrdersAreNotOpenAnymore() {
        UUID id = UUID.randomUUID();
        orders.submit(order(id, line(beer, 1)), waiterId);
        assertThat(orders.openOrdersOfTable(tableId)).extracting(OrderView::id).containsExactly(id);

        assertThat(orders.complete(id).status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orders.openOrdersOfTable(tableId)).isEmpty();
        assertThatThrownBy(() -> orders.complete(id)).isInstanceOf(OrderRejectedException.class);
    }

    @Test
    void invalidOrdersAreRejected() {
        assertThatThrownBy(() -> orders.submit(order(UUID.randomUUID(), line(noStation, 1)), waiterId))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("no station");

        beer.setActive(false);
        articles.saveAndFlush(beer);
        assertThatThrownBy(() -> orders.submit(order(UUID.randomUUID(), line(beer, 1)), waiterId))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("inactive");

        assertThatThrownBy(() -> orders.submit(
                new SubmitOrder(UUID.randomUUID(), tableId + 999, null, List.of(line(schnitzel, 1))), waiterId))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("table");
    }

    @Test
    void modifiersMustBelongToTheArticleAndRespectSingleSelect() {
        long rare = cooking.getModifiers().get(0).getId();
        long medium = cooking.getModifiers().get(1).getId();
        long cheese = extras.getModifiers().get(0).getId();
        long bacon = extras.getModifiers().get(1).getId();

        OrderView ok = orders.submit(order(UUID.randomUUID(),
                new NewItem(schnitzel.getId(), 1, "ohne Salz", 2, Set.of(rare, cheese, bacon))), waiterId).order();
        assertThat(ok.items().get(0).modifierIds()).containsExactlyInAnyOrder(rare, cheese, bacon);
        assertThat(ok.items().get(0).course()).isEqualTo(2);

        assertThatThrownBy(() -> orders.submit(order(UUID.randomUUID(),
                new NewItem(schnitzel.getId(), 1, null, null, Set.of(rare, medium))), waiterId))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("Only one choice");
        assertThatThrownBy(() -> orders.submit(order(UUID.randomUUID(),
                new NewItem(beer.getId(), 1, null, null, Set.of(rare))), waiterId))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("not available");
    }

    @Test
    void ticketStateMachine() {
        OrderView view = orders.submit(order(UUID.randomUUID(), line(beer, 1)), waiterId).order();
        long ticket = ticketOf(view, barId).id();

        assertThat(tickets.advance(ticket).status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(tickets.advance(ticket).status()).isEqualTo(TicketStatus.DONE);
        assertThatThrownBy(() -> tickets.advance(ticket)).isInstanceOf(IllegalStateException.class);
        assertThat(tickets.recall(ticket).status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThatThrownBy(() -> tickets.recall(ticket)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restFlow() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                {"id":"%s","tableId":%d,"items":[{"articleId":%d,"quantity":2},{"articleId":%d,"quantity":1}]}
                """.formatted(id, tableId, schnitzel.getId(), beer.getId());

        mvc.perform(post("/api/v1/orders").with(asWaiter()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        // retry: same order, no new tickets
        mvc.perform(post("/api/v1/orders").with(asWaiter()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/" + id).with(asWaiter())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tables/" + tableId + "/orders").with(asWaiter()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/orders/" + id + "/items").with(asWaiter()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionNo\":2,\"items\":[{\"articleId\":" + beer.getId()
                                + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/orders/" + id + "/items").with(asWaiter()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionNo\":2,\"items\":[{\"articleId\":" + beer.getId()
                                + ",\"quantity\":1}]}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/orders/" + id + "/completion").with(asWaiter())).andExpect(status().isOk());
    }

    @Test
    void restValidationAndErrors() throws Exception {
        mvc.perform(post("/api/v1/orders").with(asWaiter()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + UUID.randomUUID() + "\",\"tableId\":" + tableId + ",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").with(asWaiter()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + UUID.randomUUID() + "\",\"tableId\":" + tableId
                                + ",\"items\":[{\"articleId\":" + noStation.getId() + ",\"quantity\":1}]}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/orders/" + UUID.randomUUID()).with(asWaiter()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asWaiter() {
        return jwt().jwt(j -> j.subject(Long.toString(waiterId))).authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WAITER"));
    }

    private SubmitOrder order(UUID id, NewItem... lines) {
        return new SubmitOrder(id, tableId, null, List.of(lines));
    }

    private static NewItem line(Article article, int quantity) {
        return new NewItem(article.getId(), quantity, null, null, null);
    }

    private static TicketSummary ticketOf(OrderView view, long stationId) {
        return view.tickets().stream().filter(t -> t.stationId() == stationId && t.kind() != TicketKind.STORNO)
                .findFirst().orElseThrow();
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
        article.setPrice(new BigDecimal("10.00"));
        return article;
    }
}
