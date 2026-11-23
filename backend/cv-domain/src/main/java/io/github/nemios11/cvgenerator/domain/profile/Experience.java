package io.github.nemios11.cvgenerator.domain.profile;

import static io.github.nemios11.cvgenerator.domain.profile.Checks.localizedList;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalLocalized;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireId;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireLocalized;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireText;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.tags;

import java.util.List;
import java.util.UUID;

/** A job held by the user. */
public record Experience(
        UUID id,
        String company,
        LocalizedText role,
        LocalizedText location,
        Period period,
        List<LocalizedText> bullets,
        List<String> technologies) {

    public Experience {
        id = requireId("id", id);
        company = requireText("company", company, ProfileLimits.NAME_LENGTH);
        role = requireLocalized("role", role);
        location = optionalLocalized(location);
        if (period == null) {
            throw new InvalidProfileException("period", "is required");
        }
        bullets = localizedList("bullets", bullets, ProfileLimits.BULLETS);
        technologies = tags("technologies", technologies);
    }
}
