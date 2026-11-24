package io.github.nemios11.cvgenerator.domain.profile;

import static io.github.nemios11.cvgenerator.domain.profile.Checks.localizedList;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalLocalized;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalUrl;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireId;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireLocalized;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.tags;

import java.util.List;
import java.util.UUID;

/** A project worth showcasing: context, personal contribution and outcome. */
public record Project(
        UUID id,
        LocalizedText name,
        LocalizedText description,
        List<LocalizedText> highlights,
        List<String> technologies,
        String url) {

    public Project {
        id = requireId("id", id);
        name = requireLocalized("name", name);
        description = optionalLocalized(description);
        highlights = localizedList("highlights", highlights, ProfileLimits.BULLETS);
        technologies = tags("technologies", technologies);
        url = optionalUrl("url", url);
    }
}
