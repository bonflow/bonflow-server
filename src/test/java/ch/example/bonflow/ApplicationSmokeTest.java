package ch.example.bonflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the whole application against a real PostgreSQL (Flyway migrations run), checks that
 * the API requires a token and exports the OpenAPI spec to {@code target/openapi.json}, which
 * CI publishes as the contract for bonflow-app.
 */
@SpringBootTest(properties = {
        "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123",
        "bonflow.security.bootstrap-admin-password=test"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationSmokeTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/masterdata")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportsOpenApiSpec() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(spec).contains("BonFlow API");
        Path target = Path.of("target", "openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, spec);
    }
}
