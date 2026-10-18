package com.briefwise.shared.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the generated OpenAPI spec. A relative server URL keeps the spec independent of host and port, so
 * the committed docs/openapi.json does not change between machines (checked by {@code OpenApiSpecIT}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class OpenApiConfig {

    @Bean
    OpenAPI briefwiseOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Briefwise API").version("v0"))
                .servers(List.of(new Server().url("/")));
    }
}
