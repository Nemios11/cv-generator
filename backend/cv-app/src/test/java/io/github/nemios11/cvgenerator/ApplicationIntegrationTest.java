package io.github.nemios11.cvgenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nemios11.cvgenerator.profile.ProfileFixtures;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Full application against a real PostgreSQL (Testcontainers) with Flyway
 * migrations. Skipped automatically where no container runtime is available;
 * always runs in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static final RequestPostProcessor CAROL = jwt().jwt(t -> t.subject("carol"));
    static final RequestPostProcessor DAVE = jwt().jwt(t -> t.subject("dave"));

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        mvc.perform(get("/internal").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void profileIsPersistedPerUserWithOptimisticLocking() throws Exception {
        mvc.perform(put("/api/profile").with(CAROL).contentType(MediaType.APPLICATION_JSON)
                        .content(ProfileFixtures.VALID))
                .andExpect(status().isCreated());

        // Stored as a JSONB document keyed by the owner only.
        String stored = jdbc.sql("SELECT document::text FROM career_profile WHERE owner_id = 'carol'")
                .query(String.class).single();
        assertThat(stored).contains("Acme Software").doesNotContain("carol");

        mvc.perform(get("/api/profile").with(CAROL))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.experiences[0].technologies[1]").value("Spring Boot"));

        mvc.perform(get("/api/profile").with(DAVE)).andExpect(status().isNotFound());

        mvc.perform(put("/api/profile").with(CAROL).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(ProfileFixtures.named("Carol Example")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
        mvc.perform(put("/api/profile").with(CAROL).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(ProfileFixtures.VALID))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(delete("/api/profile").with(CAROL)).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT count(*) FROM career_profile WHERE owner_id = 'carol'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void publishesTheOpenApiDescription() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/profile']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.keycloak.type").value("oauth2"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Swagger UI")));
    }
}
