package io.github.nemios11.cvgenerator.domain.profile;

import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalLocalized;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireId;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireLocalized;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireText;

import java.time.YearMonth;
import java.util.UUID;

/**
 * A degree or course. Dates are optional (many CVs show only the year of
 * completion), but when both are present the end must not precede the start.
 */
public record Education(
        UUID id,
        LocalizedText qualification,
        String institution,
        LocalizedText location,
        YearMonth start,
        YearMonth end) {

    public Education {
        id = requireId("id", id);
        qualification = requireLocalized("qualification", qualification);
        institution = requireText("institution", institution, ProfileLimits.NAME_LENGTH);
        location = optionalLocalized(location);
        if (start != null && end != null && end.isBefore(start)) {
            throw new InvalidProfileException("end", "must not be before the start");
        }
    }
}
