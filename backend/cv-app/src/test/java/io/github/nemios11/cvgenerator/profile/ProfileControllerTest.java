package io.github.nemios11.cvgenerator.profile;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nemios11.cvgenerator.security.SecurityConfig;
import io.github.nemios11.cvgenerator.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, ProfileService.class, ProfileControllerTest.Store.class})
class ProfileControllerTest {

    @TestConfiguration
    static class Store {
        @Bean
        InMemoryProfileStore profileStore() {
            return new InMemoryProfileStore();
        }
    }

    static final RequestPostProcessor ALICE = jwt().jwt(t -> t.subject("alice"));
    static final RequestPostProcessor BOB = jwt().jwt(t -> t.subject("bob"));

    @Autowired
    MockMvc mvc;

    @Autowired
    InMemoryProfileStore store;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void reset() {
        store.clear();
    }

    static MockHttpServletRequestBuilder putJson(String body) {
        return put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(putJson(ProfileFixtures.VALID)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void readingAMissingProfileIs404() throws Exception {
        mvc.perform(get("/api/profile").with(ALICE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Profile not found"));
    }

    @Test
    void createsThenReplacesTheProfileWithVersionedETags() throws Exception {
        mvc.perform(putJson(ProfileFixtures.VALID).with(ALICE))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Location", "/api/profile"))
                .andExpect(jsonPath("$.header.fullName").value("Alex Example"))
                .andExpect(jsonPath("$.experiences[0].id").isNotEmpty())
                .andExpect(jsonPath("$.experiences[0].end").doesNotExist())
                .andExpect(jsonPath("$.languages[0].level").value("C1"));

        mvc.perform(putJson(ProfileFixtures.named("Alex Renamed")).with(ALICE).header("If-Match", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));

        mvc.perform(get("/api/profile").with(ALICE))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.header.fullName").value("Alex Renamed"))
                .andExpect(jsonPath("$.experiences").isEmpty());
    }

    @Test
    void rejectsAStaleIfMatch() throws Exception {
        mvc.perform(putJson(ProfileFixtures.VALID).with(ALICE)).andExpect(status().isCreated());
        mvc.perform(putJson(ProfileFixtures.VALID).with(ALICE)).andExpect(status().isOk());

        mvc.perform(putJson(ProfileFixtures.VALID).with(ALICE).header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.title").value("Version conflict"));
        mvc.perform(putJson(ProfileFixtures.VALID).with(ALICE).header("If-Match", "garbage"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void reportsTheFullPathOfAnInvalidField() throws Exception {
        mvc.perform(putJson(ProfileFixtures.END_BEFORE_START).with(ALICE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid profile"))
                .andExpect(jsonPath("$.field").value("experiences[0].period.end"));

        mvc.perform(putJson(ProfileFixtures.BAD_MONTH).with(ALICE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("experiences[0].start"));

        mvc.perform(putJson("{}").with(ALICE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("header"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(putJson("{not json").with(ALICE)).andExpect(status().isBadRequest());
    }

    @Test
    void usersCannotSeeOrDeleteEachOthersProfiles() throws Exception {
        mvc.perform(putJson(ProfileFixtures.VALID).with(ALICE)).andExpect(status().isCreated());

        mvc.perform(get("/api/profile").with(BOB)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/profile").with(BOB)).andExpect(status().isNotFound());
        mvc.perform(putJson(ProfileFixtures.named("Bob Example")).with(BOB)).andExpect(status().isCreated());

        mvc.perform(get("/api/profile").with(ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.fullName").value("Alex Example"));
        mvc.perform(get("/api/profile").with(BOB))
                .andExpect(jsonPath("$.header.fullName").value("Bob Example"));
    }

    @Test
    void deletesTheProfile() throws Exception {
        mvc.perform(putJson(ProfileFixtures.VALID).with(ALICE)).andExpect(status().isCreated());

        mvc.perform(delete("/api/profile").with(ALICE)).andExpect(status().isNoContent());
        mvc.perform(get("/api/profile").with(ALICE)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/profile").with(ALICE)).andExpect(status().isNotFound());
    }
}
