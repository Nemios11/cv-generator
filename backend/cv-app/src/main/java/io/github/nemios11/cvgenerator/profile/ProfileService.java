package io.github.nemios11.cvgenerator.profile;

import io.github.nemios11.cvgenerator.domain.UserId;
import io.github.nemios11.cvgenerator.domain.profile.CareerProfile;
import io.github.nemios11.cvgenerator.profile.ProfileStore.StoredProfile;
import org.springframework.stereotype.Service;

/**
 * Use cases on the caller's own career profile. The caller identity always comes
 * from the authenticated token, never from the request body or path, so a user
 * cannot address another user's profile.
 */
@Service
public class ProfileService {

    private final ProfileStore store;

    public ProfileService(ProfileStore store) {
        this.store = store;
    }

    public StoredProfile get(UserId caller) {
        return store.find(caller).orElseThrow(ProfileNotFoundException::new);
    }

    /**
     * Creates or replaces the caller's profile.
     *
     * @param expectedVersion the version the client last read ({@code If-Match});
     *                        {@code null} means "create, or replace whatever is there"
     */
    public SaveResult save(UserId caller, CareerProfile profile, Long expectedVersion) {
        if (!profile.isOwnedBy(caller)) {
            // Defensive: the web layer always builds the profile for the caller.
            throw new IllegalArgumentException("profile must belong to the caller");
        }
        if (expectedVersion != null) {
            return store.update(profile, expectedVersion)
                    .map(stored -> new SaveResult(stored, false))
                    .orElseThrow(ProfileVersionConflictException::new);
        }
        var existing = store.find(caller);
        if (existing.isEmpty()) {
            return store.insert(profile)
                    .map(stored -> new SaveResult(stored, true))
                    // Another request created it in the meantime.
                    .orElseThrow(ProfileVersionConflictException::new);
        }
        return store.update(profile, existing.get().version())
                .map(stored -> new SaveResult(stored, false))
                .orElseThrow(ProfileVersionConflictException::new);
    }

    /** Erases the caller's profile (GDPR art. 17). */
    public void delete(UserId caller) {
        if (!store.delete(caller)) {
            throw new ProfileNotFoundException();
        }
    }

    public record SaveResult(StoredProfile stored, boolean created) {}

    public static class ProfileNotFoundException extends RuntimeException {
        public ProfileNotFoundException() {
            super("profile not found");
        }
    }

    public static class ProfileVersionConflictException extends RuntimeException {
        public ProfileVersionConflictException() {
            super("the profile was modified by another request");
        }
    }
}
