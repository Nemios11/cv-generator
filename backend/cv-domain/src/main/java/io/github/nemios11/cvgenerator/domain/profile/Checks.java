package io.github.nemios11.cvgenerator.domain.profile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Small validation helpers shared by the profile model. Package-private by design. */
final class Checks {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ().-]{6,30}$");

    private Checks() {}

    /** Trims; null becomes null. */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String requireText(String field, String value, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new InvalidProfileException(field, "is required");
        }
        return maxLength(field, trimmed, maxLength);
    }

    static String optionalText(String field, String value, int maxLength) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : maxLength(field, trimmed, maxLength);
    }

    static String maxLength(String field, String value, int maxLength) {
        if (value.length() > maxLength) {
            throw new InvalidProfileException(field, "must be at most " + maxLength + " characters");
        }
        return value;
    }

    static LocalizedText requireLocalized(String field, LocalizedText value) {
        if (value == null || value.isBlank()) {
            throw new InvalidProfileException(field, "is required in at least one language");
        }
        return value;
    }

    static LocalizedText optionalLocalized(LocalizedText value) {
        return value == null ? LocalizedText.EMPTY : value;
    }

    static String optionalEmail(String field, String value) {
        String trimmed = optionalText(field, value, 254);
        if (trimmed != null && !EMAIL.matcher(trimmed).matches()) {
            throw new InvalidProfileException(field, "is not a valid e-mail address");
        }
        return trimmed;
    }

    static String optionalPhone(String field, String value) {
        String trimmed = optionalText(field, value, 30);
        if (trimmed != null && !PHONE.matcher(trimmed).matches()) {
            throw new InvalidProfileException(field, "is not a valid phone number");
        }
        return trimmed;
    }

    static String optionalUrl(String field, String value) {
        String trimmed = optionalText(field, value, ProfileLimits.URL_LENGTH);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null) {
                throw new InvalidProfileException(field, "must be an http(s) URL");
            }
        } catch (URISyntaxException e) {
            throw new InvalidProfileException(field, "must be an http(s) URL");
        }
        return trimmed;
    }

    /** Copies a list, rejecting null elements and enforcing a maximum size. */
    static <T> List<T> list(String field, List<T> values, int maxSize) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > maxSize) {
            throw new InvalidProfileException(field, "must contain at most " + maxSize + " items");
        }
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null) {
                throw new InvalidProfileException(field + "[" + i + "]", "must not be null");
            }
        }
        return List.copyOf(values);
    }

    /** Localized bullet-like texts: blank entries are not allowed. */
    static List<LocalizedText> localizedList(String field, List<LocalizedText> values, int maxSize) {
        List<LocalizedText> copy = list(field, values, maxSize);
        for (int i = 0; i < copy.size(); i++) {
            requireLocalized(field + "[" + i + "]", copy.get(i));
        }
        return copy;
    }

    /** Tags such as technologies: trimmed, non-blank, unique ignoring case. */
    static List<String> tags(String field, List<String> values) {
        List<String> copy = list(field, values, ProfileLimits.TAGS);
        List<String> result = new ArrayList<>(copy.size());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < copy.size(); i++) {
            String tag = requireText(field + "[" + i + "]", copy.get(i), ProfileLimits.TAG_LENGTH);
            if (!seen.add(tag.toLowerCase(Locale.ROOT))) {
                throw new InvalidProfileException(field + "[" + i + "]", "is duplicated");
            }
            result.add(tag);
        }
        return List.copyOf(result);
    }

    static <T> void uniqueIds(String field, List<T> values, Function<T, UUID> id) {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            if (!seen.add(id.apply(values.get(i)))) {
                throw new InvalidProfileException(field + "[" + i + "].id", "is duplicated");
            }
        }
    }

    static UUID requireId(String field, UUID id) {
        if (id == null) {
            throw new InvalidProfileException(field, "is required");
        }
        return id;
    }
}
