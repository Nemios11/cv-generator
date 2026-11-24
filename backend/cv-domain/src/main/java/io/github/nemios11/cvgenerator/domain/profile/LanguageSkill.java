package io.github.nemios11.cvgenerator.domain.profile;

import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireId;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireLocalized;

import java.util.UUID;

/** A spoken language with a CEFR level (or native). */
public record LanguageSkill(UUID id, LocalizedText language, Level level) {

    /** Common European Framework of Reference levels, plus native speaker. */
    public enum Level { A1, A2, B1, B2, C1, C2, NATIVE }

    public LanguageSkill {
        id = requireId("id", id);
        language = requireLocalized("language", language);
        if (level == null) {
            throw new InvalidProfileException("level", "is required");
        }
    }
}
