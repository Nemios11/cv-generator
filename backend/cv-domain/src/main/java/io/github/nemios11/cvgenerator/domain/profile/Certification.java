package io.github.nemios11.cvgenerator.domain.profile;

import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalUrl;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireId;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireLocalized;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireText;

import java.time.YearMonth;
import java.util.UUID;

/** A certification. Expiry, when present, cannot precede the issue date. */
public record Certification(
        UUID id,
        LocalizedText name,
        String issuer,
        YearMonth issued,
        YearMonth expires,
        String credentialUrl) {

    public Certification {
        id = requireId("id", id);
        name = requireLocalized("name", name);
        issuer = requireText("issuer", issuer, ProfileLimits.NAME_LENGTH);
        if (issued != null && expires != null && expires.isBefore(issued)) {
            throw new InvalidProfileException("expires", "must not be before the issue date");
        }
        credentialUrl = optionalUrl("credentialUrl", credentialUrl);
    }
}
