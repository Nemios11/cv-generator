package io.github.nemios11.cvgenerator;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Deployed-environment layout: actuator on a separate management port, as in
 * deploy/compose.yaml. Metrics are readable there without a token (that port is
 * internal to the environment) and not on the application port.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.server.port=0",
            "management.endpoints.web.exposure.include=health,prometheus"
        })
@Testcontainers(disabledWithoutDocker = true)
class ManagementPortIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Value("${local.server.port}")
    int serverPort;

    @Value("${local.management.port}")
    int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void metricsAreOnTheManagementPortOnly() throws Exception {
        // One application request, so that the HTTP server metric exists.
        assertThat(get(serverPort, "/api/me").statusCode()).isEqualTo(401);

        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("http_server_requests_seconds_count");
        assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);

        assertThat(get(serverPort, "/actuator/prometheus").statusCode()).isNotEqualTo(200);
    }

    @Test
    void theApiStillRequiresATokenOnTheApplicationPort() throws Exception {
        assertThat(get(serverPort, "/api/profile").statusCode()).isEqualTo(401);
    }
}
