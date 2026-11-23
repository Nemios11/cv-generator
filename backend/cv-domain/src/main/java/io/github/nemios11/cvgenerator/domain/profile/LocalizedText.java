package io.github.nemios11.cvgenerator.domain.profile;

/**
 * A text in the supported languages. Each translation is optional (an empty
 * string when missing) so that a profile can be filled in one language first;
 * required fields demand at least one non-blank translation.
 */
public record LocalizedText(String it, String en) {

    public static final LocalizedText EMPTY = new LocalizedText("", "");

    public LocalizedText {
        it = normalize("it", it);
        en = normalize("en", en);
    }

    public static LocalizedText of(String it, String en) {
        return new LocalizedText(it, en);
    }

    /** Same text in every language, e.g. a product name. */
    public static LocalizedText same(String text) {
        return new LocalizedText(text, text);
    }

    public boolean isBlank() {
        return it.isEmpty() && en.isEmpty();
    }

    private static String normalize(String lang, String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        if (trimmed.length() > ProfileLimits.LONG_TEXT_LENGTH) {
            throw new InvalidProfileException(lang, "must be at most " + ProfileLimits.LONG_TEXT_LENGTH + " characters");
        }
        return trimmed;
    }
}
