package io.github.nemios11.cvgenerator.domain.profile;

/**
 * A profile invariant was violated. {@code field} is a path such as
 * {@code experiences[2].period.end} so that clients can point at the offending input.
 */
public class InvalidProfileException extends RuntimeException {

    private final String field;
    private final String reason;

    public InvalidProfileException(String field, String reason) {
        super(field + ": " + reason);
        this.field = field;
        this.reason = reason;
    }

    public String field() {
        return field;
    }

    public String reason() {
        return reason;
    }

    /** The same violation, located inside {@code prefix} (e.g. {@code experiences[2]}). */
    public InvalidProfileException within(String prefix) {
        return new InvalidProfileException(prefix + "." + field, reason);
    }
}
