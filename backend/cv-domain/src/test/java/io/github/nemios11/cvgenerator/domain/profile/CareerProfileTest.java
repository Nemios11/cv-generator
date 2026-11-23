package io.github.nemios11.cvgenerator.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nemios11.cvgenerator.domain.UserId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CareerProfileTest {

    static final UserId OWNER = new UserId("user-1");

    static Experience experience(UUID id) {
        return new Experience(id, "Acme", LocalizedText.same("Developer"), null,
                Period.ongoing(YearMonth.of(2023, 1)), List.of(), List.of("Java"));
    }

    static CareerProfile withExperiences(List<Experience> experiences) {
        return new CareerProfile(OWNER, CareerProfile.empty(OWNER, "Alex Example").header(),
                experiences, null, null, null, null, null);
    }

    @Test
    void emptyProfileNeedsOnlyANameAndHasEmptyCollections() {
        CareerProfile profile = CareerProfile.empty(OWNER, "  Alex Example  ");

        assertThat(profile.header().fullName()).isEqualTo("Alex Example");
        assertThat(profile.experiences()).isEmpty();
        assertThat(profile.languages()).isEmpty();
        assertThat(profile.isOwnedBy(OWNER)).isTrue();
        assertThat(profile.isOwnedBy(new UserId("someone-else"))).isFalse();
    }

    @Test
    void requiresAnOwner() {
        assertThatThrownBy(() -> CareerProfile.empty(null, "Alex")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void collectionsAreImmutableCopiesThatKeepTheUserOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<Experience> source = new ArrayList<>(List.of(experience(second), experience(first)));

        CareerProfile profile = withExperiences(source);
        source.clear();

        assertThat(profile.experiences()).extracting(Experience::id).containsExactly(second, first);
        assertThatThrownBy(() -> profile.experiences().add(experience(UUID.randomUUID())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateEntryIds() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> withExperiences(List.of(experience(id), experience(id))))
                .isInstanceOfSatisfying(InvalidProfileException.class,
                        e -> assertThat(e.field()).isEqualTo("experiences[1].id"));
    }

    @Test
    void rejectsTooManyEntries() {
        List<Experience> many = new ArrayList<>();
        for (int i = 0; i <= ProfileLimits.ENTRIES; i++) {
            many.add(experience(UUID.randomUUID()));
        }
        assertThatThrownBy(() -> withExperiences(many)).isInstanceOf(InvalidProfileException.class);
    }

    @Nested
    class Header {

        @Test
        void requiresAFullName() {
            assertThatThrownBy(() -> CareerProfile.empty(OWNER, " "))
                    .isInstanceOfSatisfying(InvalidProfileException.class,
                            e -> assertThat(e.field()).isEqualTo("fullName"));
        }

        @Test
        void contactsAreOptionalButValidatedWhenPresent() {
            ProfileHeader ok = new ProfileHeader("Alex", null, null, "alex@example.com", "+00 123 456 789",
                    "https://example.com", null, "", null);
            assertThat(ok.github()).isNull();
            assertThat(ok.headline()).isEqualTo(LocalizedText.EMPTY);

            assertThatThrownBy(() -> new ProfileHeader("Alex", null, null, "not-an-email", null, null, null, null, null))
                    .hasMessageContaining("email");
            assertThatThrownBy(() -> new ProfileHeader("Alex", null, null, null, "call me", null, null, null, null))
                    .hasMessageContaining("phone");
            assertThatThrownBy(() -> new ProfileHeader("Alex", null, null, null, null, "javascript:alert(1)", null, null, null))
                    .hasMessageContaining("website");
            assertThatThrownBy(() -> new ProfileHeader("Alex", null, null, null, null, null, "ftp://example.com", null, null))
                    .hasMessageContaining("linkedin");
        }
    }

    @Nested
    class Entries {

        @Test
        void periodEndCannotPrecedeStart() {
            assertThatThrownBy(() -> new Period(YearMonth.of(2024, 5), YearMonth.of(2024, 4)))
                    .isInstanceOfSatisfying(InvalidProfileException.class, e -> assertThat(e.field()).isEqualTo("end"));
            assertThat(new Period(YearMonth.of(2024, 5), YearMonth.of(2024, 5)).isOngoing()).isFalse();
            assertThat(Period.ongoing(YearMonth.of(2024, 5)).isOngoing()).isTrue();
        }

        @Test
        void experienceRequiresCompanyRoleAndPeriod() {
            UUID id = UUID.randomUUID();
            Period period = Period.ongoing(YearMonth.of(2020, 1));
            assertThatThrownBy(() -> new Experience(id, "", LocalizedText.same("Dev"), null, period, null, null))
                    .hasMessageContaining("company");
            assertThatThrownBy(() -> new Experience(id, "Acme", LocalizedText.EMPTY, null, period, null, null))
                    .hasMessageContaining("role");
            assertThatThrownBy(() -> new Experience(id, "Acme", LocalizedText.same("Dev"), null, null, null, null))
                    .hasMessageContaining("period");
        }

        @Test
        void aRequiredTextNeedsAtLeastOneLanguage() {
            Experience italianOnly = new Experience(UUID.randomUUID(), "Acme", LocalizedText.of("Sviluppatore", null),
                    null, Period.ongoing(YearMonth.of(2020, 1)), null, null);
            assertThat(italianOnly.role().en()).isEmpty();
        }

        @Test
        void bulletsCannotBeBlank() {
            assertThatThrownBy(() -> new Experience(UUID.randomUUID(), "Acme", LocalizedText.same("Dev"), null,
                    Period.ongoing(YearMonth.of(2020, 1)), List.of(LocalizedText.EMPTY), null))
                    .isInstanceOfSatisfying(InvalidProfileException.class,
                            e -> assertThat(e.field()).isEqualTo("bullets[0]"));
        }

        @Test
        void technologiesAreTrimmedAndUniqueIgnoringCase() {
            Experience ok = new Experience(UUID.randomUUID(), "Acme", LocalizedText.same("Dev"), null,
                    Period.ongoing(YearMonth.of(2020, 1)), null, List.of(" Java ", "Kafka"));
            assertThat(ok.technologies()).containsExactly("Java", "Kafka");

            assertThatThrownBy(() -> new Experience(UUID.randomUUID(), "Acme", LocalizedText.same("Dev"), null,
                    Period.ongoing(YearMonth.of(2020, 1)), null, List.of("Java", "java")))
                    .hasMessageContaining("technologies[1]");
        }

        @Test
        void educationAndCertificationDatesAreConsistent() {
            assertThatThrownBy(() -> new Education(UUID.randomUUID(), LocalizedText.same("BSc"), "Uni", null,
                    YearMonth.of(2020, 1), YearMonth.of(2019, 1))).hasMessageContaining("end");
            assertThatThrownBy(() -> new Certification(UUID.randomUUID(), LocalizedText.same("Cert"), "Issuer",
                    YearMonth.of(2024, 1), YearMonth.of(2023, 1), null)).hasMessageContaining("expires");
        }

        @Test
        void languageNeedsALevel() {
            assertThatThrownBy(() -> new LanguageSkill(UUID.randomUUID(), LocalizedText.of("Inglese", "English"), null))
                    .hasMessageContaining("level");
        }

        @Test
        void textsHaveAnUpperBound() {
            String tooLong = "x".repeat(ProfileLimits.LONG_TEXT_LENGTH + 1);
            assertThatThrownBy(() -> LocalizedText.of(tooLong, "")).isInstanceOf(InvalidProfileException.class);
        }
    }

    @Test
    void violationsCanBeLocatedInsideAParentPath() {
        InvalidProfileException e = new InvalidProfileException("end", "must not be before the start");
        InvalidProfileException located = e.within("experiences[2].period");
        assertThat(located.field()).isEqualTo("experiences[2].period.end");
        assertThat(located.reason()).isEqualTo("must not be before the start");
    }
}
