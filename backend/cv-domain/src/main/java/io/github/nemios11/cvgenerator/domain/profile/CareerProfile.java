package io.github.nemios11.cvgenerator.domain.profile;

import static io.github.nemios11.cvgenerator.domain.profile.Checks.list;
import static io.github.nemios11.cvgenerator.domain.profile.Checks.uniqueIds;

import io.github.nemios11.cvgenerator.domain.UserId;
import java.util.List;
import java.util.Objects;

/**
 * The single source of truth for a user's professional history, from which every
 * CV variant is derived. Aggregate root: it is always loaded and saved as a whole.
 *
 * <p>Invariants:
 * <ul>
 *   <li>owned by exactly one user;</li>
 *   <li>every entry is individually valid (see the entry types);</li>
 *   <li>collections are bounded and entry ids are unique within each collection;</li>
 *   <li>collections keep the order chosen by the user.</li>
 * </ul>
 */
public record CareerProfile(
        UserId owner,
        ProfileHeader header,
        List<Experience> experiences,
        List<Project> projects,
        List<SkillGroup> skillGroups,
        List<Education> education,
        List<Certification> certifications,
        List<LanguageSkill> languages) {

    public CareerProfile {
        Objects.requireNonNull(owner, "owner must not be null");
        if (header == null) {
            throw new InvalidProfileException("header", "is required");
        }
        experiences = list("experiences", experiences, ProfileLimits.ENTRIES);
        projects = list("projects", projects, ProfileLimits.ENTRIES);
        skillGroups = list("skillGroups", skillGroups, ProfileLimits.ENTRIES);
        education = list("education", education, ProfileLimits.ENTRIES);
        certifications = list("certifications", certifications, ProfileLimits.ENTRIES);
        languages = list("languages", languages, ProfileLimits.ENTRIES);

        uniqueIds("experiences", experiences, Experience::id);
        uniqueIds("projects", projects, Project::id);
        uniqueIds("skillGroups", skillGroups, SkillGroup::id);
        uniqueIds("education", education, Education::id);
        uniqueIds("certifications", certifications, Certification::id);
        uniqueIds("languages", languages, LanguageSkill::id);
    }

    /** A new profile with only the mandatory header. */
    public static CareerProfile empty(UserId owner, String fullName) {
        return new CareerProfile(owner,
                new ProfileHeader(fullName, null, null, null, null, null, null, null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public boolean isOwnedBy(UserId user) {
        return owner.equals(user);
    }
}
