package io.github.nemios11.cvgenerator.me;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the full application context (security included) without contacting the
 * identity provider: the JwtDecoder is mocked and tokens are injected by
 * spring-security-test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void rejectsAnonymousCalls() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheCallerIdentityFromTheToken() throws Exception {
        mvc.perform(get("/api/me").with(jwt().jwt(token -> token
                        .subject("user-123")
                        .claim("preferred_username", "alex")
                        .claim("name", "Alex Example")
                        .claim("email", "alex@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.username").value("alex"))
                .andExpect(jsonPath("$.name").value("Alex Example"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        mvc.perform(get("/internal").with(jwt())).andExpect(status().isForbidden());
    }
}
