package io.github.nemios11.cvgenerator.domain.profile;

import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalEmail;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalLocalized;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalPhone;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.optionalUrl;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.requireText;

/**
 * Identity and contact block shown at the top of every CV.
 * Only the full name is mandatory: contacts are optional by design
 * (data minimisation), and each one the user adds is validated.
 */
public record ProfileHeader(
        String fullName,
        LocalizedText headline,
        LocalizedText location,
        String email,
        String phone,
        String website,
        String linkedin,
        String github,
        LocalizedText summary) {

    public ProfileHeader {
        fullName = requireText("fullName", fullName, ProfileLimits.NAME_LENGTH);
        headline = optionalLocalized(headline);
        location = optionalLocalized(location);
        email = optionalEmail("email", email);
        phone = optionalPhone("phone", phone);
        website = optionalUrl("website", website);
        linkedin = optionalUrl("linkedin", linkedin);
        github = optionalUrl("github", github);
        summary = optionalLocalized(summary);
    }
}
