package io.github.nemios11.cvgenerator.domain.profile;

import java.time.YearMonth;

/**
 * A month-precision time span. {@code end == null} means "ongoing".
 * Invariant: the end is not before the start.
 */
public record Period(YearMonth start, YearMonth end) {

    public Period {
        if (start == null) {
            throw new InvalidProfileException("start", "is required");
        }
        if (end != null && end.isBefore(start)) {
            throw new InvalidProfileException("end", "must not be before the start");
        }
    }

    public static Period ongoing(YearMonth start) {
        return new Period(start, null);
    }

    public boolean isOngoing() {
        return end == null;
    }
}
