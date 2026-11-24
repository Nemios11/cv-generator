package io.github.nemios11.cvgenerator.profile;

import io.github.nemios11.cvgenerator.domain.UserId;
import io.github.nemios11.cvgenerator.profile.ProfileService.ProfileVersionConflictException;
import io.github.nemios11.cvgenerator.profile.ProfileStore.StoredProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own career profile. There is no profile id in the URL: the
 * profile is always the one owned by the authenticated user.
 * Concurrency control uses {@code ETag} / {@code If-Match}.
 */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "The authenticated user's career profile")
public class ProfileController {

    private static final URI LOCATION = URI.create("/api/profile");

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Read my profile", description = "404 if the profile has not been created yet.")
    public ResponseEntity<ProfileDto> get(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.get(caller(jwt)));
    }

    @PutMapping
    @Operation(summary = "Create or replace my profile",
            description = "201 when created, 200 when replaced. Send If-Match with the last ETag to avoid "
                    + "overwriting concurrent changes (412 on mismatch). Entry ids are generated when omitted.")
    public ResponseEntity<ProfileDto> put(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProfileDto body,
            @Parameter(description = "ETag returned by a previous read or write")
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        UserId caller = caller(jwt);
        var result = service.save(caller, ProfileMapper.toDomain(caller, body), parseVersion(ifMatch));
        if (result.created()) {
            return ResponseEntity.created(LOCATION)
                    .eTag(etag(result.stored().version()))
                    .body(ProfileMapper.toDto(result.stored().profile()));
        }
        return ok(result.stored());
    }

    @DeleteMapping
    @Operation(summary = "Delete my profile", description = "Permanently erases the profile.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        service.delete(caller(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UserId caller(Jwt jwt) {
        return new UserId(jwt.getSubject());
    }

    private static ResponseEntity<ProfileDto> ok(StoredProfile stored) {
        return ResponseEntity.ok().eTag(etag(stored.version())).body(ProfileMapper.toDto(stored.profile()));
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    /** Accepts {@code "3"} or {@code W/"3"}; anything else cannot match a current version. */
    static Long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ProfileVersionConflictException();
        }
    }
}
