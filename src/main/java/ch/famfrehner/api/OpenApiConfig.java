package ch.famfrehner.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI spec is the contract between bonflow-server and bonflow-app
 * (the Flutter client is generated from it).
 */
@Configuration
class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI bonflowOpenApi() {
        return new OpenAPI()
                .info(new Info().title("BonFlow API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
