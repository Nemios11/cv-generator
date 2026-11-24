package io.github.nemios11.cvgenerator.domain.profile;

import static io.github.nemios11.cvgenerator.domain.profile.Checks.localizedList;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireId;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireLocalized;

import java.util.List;
import java.util.UUID;

/** Skills grouped by category, deliberately without numeric self-ratings. */
public record SkillGroup(UUID id, LocalizedText name, List<LocalizedText> items) {

    public SkillGroup {
        id = requireId("id", id);
        name = requireLocalized("name", name);
        items = localizedList("items", items, ProfileLimits.SKILL_ITEMS);
    }
}
