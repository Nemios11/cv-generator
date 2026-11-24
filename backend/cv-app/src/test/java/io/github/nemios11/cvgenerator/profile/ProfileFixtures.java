package io.github.nemios11.cvgenerator.profile;

/** Fictional request bodies shared by the web and integration tests. */
public final class ProfileFixtures {

    private ProfileFixtures() {}

    public static final String VALID = """
            {
              "header": {
                "fullName": "Alex Example",
                "headline": {"it": "Sviluppatore", "en": "Developer"},
                "email": "alex@example.com",
                "website": "https://example.com"
              },
              "experiences": [{
                "company": "Acme Software",
                "role": {"it": "Senior Software Developer", "en": "Senior Software Developer"},
                "start": "2023-01",
                "bullets": [{"it": "Progetto microservizi", "en": "I design microservices"}],
                "technologies": ["Java", "Spring Boot"]
              }],
              "languages": [{"language": {"it": "Inglese", "en": "English"}, "level": "C1"}]
            }
            """;

    public static final String END_BEFORE_START = """
            {
              "header": {"fullName": "Alex Example"},
              "experiences": [{
                "company": "Acme", "role": {"en": "Developer"},
                "start": "2024-05", "end": "2024-04"
              }]
            }
            """;

    public static final String BAD_MONTH = """
            {
              "header": {"fullName": "Alex Example"},
              "experiences": [{"company": "Acme", "role": {"en": "Developer"}, "start": "05/2024"}]
            }
            """;

    public static String named(String fullName) {
        return "{\"header\": {\"fullName\": \"" + fullName + "\"}}";
    }
}
