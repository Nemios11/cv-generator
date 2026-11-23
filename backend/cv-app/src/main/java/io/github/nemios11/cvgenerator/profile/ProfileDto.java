package io.github.nemios11.cvgenerator.profile;

import io.github.nemios11.cvgenerator.domain.profile.LanguageSkill;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * JSON representation of a career profile, used both by the REST API and as the
 * stored document (see {@link #SCHEMA_VERSION}). Months use the {@code YYYY-MM}
 * format; a missing end month means "ongoing".
 */
@Schema(name = "Profile")
public record ProfileDto(
        HeaderDto header,
        List<ExperienceDto> experiences,
        List<ProjectDto> projects,
        List<SkillGroupDto> skillGroups,
        List<EducationDto> education,
        List<CertificationDto> certifications,
        List<LanguageDto> languages) {

    /** Bump when the document shape changes in a non-backward-compatible way. */
    public static final int SCHEMA_VERSION = 1;

    @Schema(name = "LocalizedText", description = "Text in Italian and English; either may be empty")
    public record TextDto(String it, String en) {}

    @Schema(name = "ProfileHeader")
    public record HeaderDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) String fullName,
            TextDto headline,
            TextDto location,
            String email,
            String phone,
            String website,
            String linkedin,
            String github,
            TextDto summary) {}

    @Schema(name = "Experience")
    public record ExperienceDto(
            @Schema(description = "Generated when omitted") UUID id,
            String company,
            TextDto role,
            TextDto location,
            @Schema(example = "2023-01") String start,
            @Schema(example = "2024-06", description = "Omit for an ongoing position") String end,
            List<TextDto> bullets,
            List<String> technologies) {}

    @Schema(name = "Project")
    public record ProjectDto(
            UUID id,
            TextDto name,
            TextDto description,
            List<TextDto> highlights,
            List<String> technologies,
            String url) {}

    @Schema(name = "SkillGroup")
    public record SkillGroupDto(UUID id, TextDto name, List<TextDto> items) {}

    @Schema(name = "Education")
    public record EducationDto(
            UUID id,
            TextDto qualification,
            String institution,
            TextDto location,
            @Schema(example = "2014-09") String start,
            @Schema(example = "2018-07") String end) {}

    @Schema(name = "Certification")
    public record CertificationDto(
            UUID id,
            TextDto name,
            String issuer,
            @Schema(example = "2024-03") String issued,
            String expires,
            String credentialUrl) {}

    @Schema(name = "Language")
    public record LanguageDto(UUID id, TextDto language, LanguageSkill.Level level) {}
}
