package io.github.nemios11.cvgenerator.profile;

import io.github.nemios11.cvgenerator.domain.UserId;
import io.github.nemios11.cvgenerator.domain.profile.CareerProfile;
import io.github.nemios11.cvgenerator.domain.profile.Certification;
import io.github.nemios11.cvgenerator.domain.profile.Education;
import io.github.nemios11.cvgenerator.domain.profile.Experience;
import io.github.nemios11.cvgenerator.domain.profile.InvalidProfileException;
import io.github.nemios11.cvgenerator.domain.profile.LanguageSkill;
import io.github.nemios11.cvgenerator.domain.profile.LocalizedText;
import io.github.nemios11.cvgenerator.domain.profile.Period;
import io.github.nemios11.cvgenerator.domain.profile.ProfileHeader;
import io.github.nemios11.cvgenerator.domain.profile.Project;
import io.github.nemios11.cvgenerator.domain.profile.SkillGroup;
import io.github.nemios11.cvgenerator.profile.ProfileDto.CertificationDto;
import io.github.nemios11.cvgenerator.profile.ProfileDto.EducationDto;
import io.github.nemios11.cvgenerator.profile.ProfileDto.ExperienceDto;
import io.github.nemios11.cvgenerator.profile.ProfileDto.HeaderDto;
import io.github.nemios11.cvgenerator.profile.ProfileDto.LanguageDto;
import io.github.nemios11.cvgenerator.profile.ProfileDto.ProjectDto;
import io.github.nemios11.cvgenerator.profile.ProfileDto.SkillGroupDto;
import io.github.nemios11.cvgenerator.profile.ProfileDto.TextDto;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Converts between {@link ProfileDto} and the domain model. Domain violations are
 * re-thrown with the full JSON path of the offending field.
 */
public final class ProfileMapper {

    private ProfileMapper() {}

    // ------------------------------------------------------------------ to domain

    public static CareerProfile toDomain(UserId owner, ProfileDto dto) {
        if (dto == null || dto.header() == null) {
            throw new InvalidProfileException("header", "is required");
        }
        return new CareerProfile(
                owner,
                at("header", () -> header(dto.header())),
                each("experiences", dto.experiences(), ProfileMapper::experience),
                each("projects", dto.projects(), ProfileMapper::project),
                each("skillGroups", dto.skillGroups(), ProfileMapper::skillGroup),
                each("education", dto.education(), ProfileMapper::education),
                each("certifications", dto.certifications(), ProfileMapper::certification),
                each("languages", dto.languages(), ProfileMapper::language));
    }

    private static ProfileHeader header(HeaderDto h) {
        return new ProfileHeader(h.fullName(), text("headline", h.headline()), text("location", h.location()),
                h.email(), h.phone(), h.website(), h.linkedin(), h.github(), text("summary", h.summary()));
    }

    private static Experience experience(ExperienceDto e) {
        YearMonth start = month("start", e.start());
        if (start == null) {
            throw new InvalidProfileException("start", "is required");
        }
        Period period = at("period", () -> new Period(start, month("end", e.end())));
        return new Experience(id(e.id()), e.company(), text("role", e.role()), text("location", e.location()),
                period, texts("bullets", e.bullets()), e.technologies());
    }

    private static Project project(ProjectDto p) {
        return new Project(id(p.id()), text("name", p.name()), text("description", p.description()),
                texts("highlights", p.highlights()), p.technologies(), p.url());
    }

    private static SkillGroup skillGroup(SkillGroupDto s) {
        return new SkillGroup(id(s.id()), text("name", s.name()), texts("items", s.items()));
    }

    private static Education education(EducationDto e) {
        return new Education(id(e.id()), text("qualification", e.qualification()), e.institution(),
                text("location", e.location()), month("start", e.start()), month("end", e.end()));
    }

    private static Certification certification(CertificationDto c) {
        return new Certification(id(c.id()), text("name", c.name()), c.issuer(),
                month("issued", c.issued()), month("expires", c.expires()), c.credentialUrl());
    }

    private static LanguageSkill language(LanguageDto l) {
        return new LanguageSkill(id(l.id()), text("language", l.language()), l.level());
    }

    private static LocalizedText text(String field, TextDto dto) {
        return dto == null ? null : at(field, () -> LocalizedText.of(dto.it(), dto.en()));
    }

    private static List<LocalizedText> texts(String field, List<TextDto> dtos) {
        return each(field, dtos, (dto) -> LocalizedText.of(dto.it(), dto.en()));
    }

    private static YearMonth month(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new InvalidProfileException(field, "must use the YYYY-MM format");
        }
    }

    private static UUID id(UUID id) {
        return id == null ? UUID.randomUUID() : id;
    }

    private static <D, T> List<T> each(String field, List<D> dtos, Function<D, T> convert) {
        if (dtos == null) {
            return List.of();
        }
        List<T> result = new ArrayList<>(dtos.size());
        for (int i = 0; i < dtos.size(); i++) {
            D dto = dtos.get(i);
            String path = field + "[" + i + "]";
            if (dto == null) {
                throw new InvalidProfileException(path, "must not be null");
            }
            result.add(at(path, () -> convert.apply(dto)));
        }
        return result;
    }

    private static <T> T at(String path, Supplier<T> step) {
        try {
            return step.get();
        } catch (InvalidProfileException e) {
            throw e.within(path);
        }
    }

    // ---------------------------------------------------------------- from domain

    public static ProfileDto toDto(CareerProfile p) {
        ProfileHeader h = p.header();
        return new ProfileDto(
                new HeaderDto(h.fullName(), dto(h.headline()), dto(h.location()), h.email(), h.phone(),
                        h.website(), h.linkedin(), h.github(), dto(h.summary())),
                map(p.experiences(), e -> new ExperienceDto(e.id(), e.company(), dto(e.role()), dto(e.location()),
                        str(e.period().start()), str(e.period().end()), dtos(e.bullets()), e.technologies())),
                map(p.projects(), x -> new ProjectDto(x.id(), dto(x.name()), dto(x.description()),
                        dtos(x.highlights()), x.technologies(), x.url())),
                map(p.skillGroups(), s -> new SkillGroupDto(s.id(), dto(s.name()), dtos(s.items()))),
                map(p.education(), e -> new EducationDto(e.id(), dto(e.qualification()), e.institution(),
                        dto(e.location()), str(e.start()), str(e.end()))),
                map(p.certifications(), c -> new CertificationDto(c.id(), dto(c.name()), c.issuer(),
                        str(c.issued()), str(c.expires()), c.credentialUrl())),
                map(p.languages(), l -> new LanguageDto(l.id(), dto(l.language()), l.level())));
    }

    private static TextDto dto(LocalizedText t) {
        return new TextDto(t.it(), t.en());
    }

    private static List<TextDto> dtos(List<LocalizedText> texts) {
        return map(texts, ProfileMapper::dto);
    }

    private static String str(YearMonth month) {
        return month == null ? null : month.toString();
    }

    private static <A, B> List<B> map(List<A> values, Function<A, B> f) {
        return values.stream().map(f).toList();
    }
}
