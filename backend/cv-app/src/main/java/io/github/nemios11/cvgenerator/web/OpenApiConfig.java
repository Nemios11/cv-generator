package io.github.nemios11.cvgenerator.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description at {@code /v3/api-docs}, Swagger UI at {@code /swagger-ui.html}.
 * Swagger UI logs in through the same Keycloak realm (authorization code + PKCE).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String SCHEME = "keycloak";

    @Bean
    OpenAPI cvGeneratorOpenApi(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                .tokenUrl(issuer + "/protocol/openid-connect/token")
                .scopes(new Scopes().addString("openid", "OpenID Connect"));
        return new OpenAPI()
                .info(new Info()
                        .title("CV Generator API")
                        .version("v1")
                        .description("Career profiles and CV generation. Every endpoint acts on the data of "
                                + "the authenticated user only."))
                .components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(authorizationCode))))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
