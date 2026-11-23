package io.github.nemios11.cvgenerator.profile;

import io.github.nemios11.cvgenerator.domain.UserId;
import io.github.nemios11.cvgenerator.domain.profile.CareerProfile;
import java.util.Optional;

/**
 * Persistence port for career profiles. Every operation is keyed by the owner:
 * there is deliberately no way to list or read profiles across users.
 */
public interface ProfileStore {

    Optional<StoredProfile> find(UserId owner);

    /**
     * Inserts a new profile.
     *
     * @return the stored profile, or empty if the owner already has one
     */
    Optional<StoredProfile> insert(CareerProfile profile);

    /**
     * Replaces the profile if its current version is {@code expectedVersion}.
     *
     * @return the stored profile, or empty if it does not exist or the version differs
     */
    Optional<StoredProfile> update(CareerProfile profile, long expectedVersion);

    /** @return {@code true} if a profile was deleted */
    boolean delete(UserId owner);

    /** A profile together with its optimistic-locking version. */
    record StoredProfile(CareerProfile profile, long version) {}
}
