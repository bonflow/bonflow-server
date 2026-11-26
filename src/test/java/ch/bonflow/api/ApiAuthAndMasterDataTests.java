package ch.bonflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.bonflow.TestcontainersConfiguration;
import ch.bonflow.masterdata.Area;
import ch.bonflow.masterdata.AreaRepository;
import ch.bonflow.masterdata.Article;
import ch.bonflow.masterdata.ArticleCategory;
import ch.bonflow.masterdata.ArticleCategoryRepository;
import ch.bonflow.masterdata.ArticleRepository;
import ch.bonflow.users.AppUser;
import ch.bonflow.users.WaiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Waiter PIN login, token handling and the ETag behaviour of the master data endpoint. */
@SpringBootTest(properties = "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiAuthAndMasterDataTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    WaiterService waiters;
    @Autowired
    AreaRepository areas;
    @Autowired
    ArticleCategoryRepository categories;
    @Autowired
    ArticleRepository articles;

    long waiterId;

    @BeforeEach
    void waiter() {
        AppUser anna = new AppUser();
        anna.setDisplayName("Anna");
        anna.setLogin("anna");
        waiterId = waiters.save(anna, "1234").getId();
    }

    @Test
    void waiterListContainsActiveWaitersOnly() throws Exception {
        AppUser inactive = new AppUser();
        inactive.setDisplayName("Bernd");
        inactive.setLogin("bernd");
        inactive.setActive(false);
        waiters.save(inactive, "4321");

        String body = mvc.perform(get("/api/v1/auth/waiters")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Anna").doesNotContain("Bernd");
    }

    @Test
    void loginWithCorrectPinReturnsWorkingTokens() throws Exception {
        String body = login("1234").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = field(body, "accessToken");

        mvc.perform(get("/api/v1/masterdata").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPinIsRejected() throws Exception {
        login("9999").andExpect(status().isUnauthorized());
    }

    @Test
    void waiterIsLockedAfterRepeatedWrongPins() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("0000").andExpect(status().isUnauthorized());
        }
        // even the correct PIN is refused while locked
        login("1234").andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void refreshTokenIssuesNewPairButIsNoBearerToken() throws Exception {
        String body = login("1234").andReturn().getResponse().getContentAsString();
        String refresh = field(body, "refreshToken");

        mvc.perform(get("/api/v1/masterdata").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void accessTokenCannotBeUsedToRefresh() throws Exception {
        String body = login("1234").andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + field(body, "accessToken") + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void masterDataSupportsEtag() throws Exception {
        Area hall = new Area();
        hall.setName("Saal");
        areas.save(hall);
        ArticleCategory drinks = new ArticleCategory();
        drinks.setName("Getränke");
        categories.save(drinks);
        Article beer = new Article();
        beer.setName("Bier");
        beer.setCategory(drinks);
        beer.setPrice(new BigDecimal("5.50"));
        articles.save(beer);

        String token = field(login("1234").andReturn().getResponse().getContentAsString(), "accessToken");

        var first = mvc.perform(get("/api/v1/masterdata").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().exists("ETag")).andReturn().getResponse();
        assertThat(first.getContentAsString()).contains("Bier").contains("Saal");
        String etag = first.getHeader("ETag");

        mvc.perform(get("/api/v1/masterdata").header("Authorization", "Bearer " + token)
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        beer.setPrice(new BigDecimal("6.00"));
        articles.saveAndFlush(beer);

        mvc.perform(get("/api/v1/masterdata").header("Authorization", "Bearer " + token)
                        .header("If-None-Match", etag))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", org.hamcrest.Matchers.not(etag)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String pin) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"waiterId\":" + waiterId + ",\"pin\":\"" + pin + "\"}"));
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).as("field %s in %s", name, json).isTrue();
        return m.group(1);
    }
}
