package io.github.nemios11.cvgenerator.me;

import io.github.nemios11.cvgenerator.domain.UserId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns the identity of the caller, derived only from the access token. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UserId id = new UserId(jwt.getSubject());
        return new MeResponse(
                id.value(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"));
    }

    /**
     * Data minimisation: only what the UI needs to greet the user. The e-mail
     * address is deliberately not exposed by this endpoint.
     */
    public record MeResponse(String id, String username, String name) {}
}
