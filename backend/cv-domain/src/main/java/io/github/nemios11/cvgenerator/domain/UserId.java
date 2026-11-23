package io.github.nemios11.cvgenerator.domain;

import java.util.Objects;

/**
 * Identity of an application user, as issued by the identity provider
 * (the OIDC {@code sub} claim). Every piece of user data is owned by exactly one
 * {@code UserId}: it is the boundary of per-user isolation.
 */
public record UserId(String value) {

    private static final int MAX_LENGTH = 255;

    public UserId {
        Objects.requireNonNull(value, "user id must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("user id must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("user id must be at most " + MAX_LENGTH + " characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
