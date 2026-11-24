package io.github.nemios11.cvgenerator.profile;

import io.github.nemios11.cvgenerator.domain.UserId;
import io.github.nemios11.cvgenerator.domain.profile.CareerProfile;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test double with the same contract as {@link JdbcProfileStore}. */
public class InMemoryProfileStore implements ProfileStore {

    private final Map<UserId, StoredProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredProfile> find(UserId owner) {
        return Optional.ofNullable(profiles.get(owner));
    }

    @Override
    public Optional<StoredProfile> insert(CareerProfile profile) {
        StoredProfile stored = new StoredProfile(profile, 0);
        return profiles.putIfAbsent(profile.owner(), stored) == null ? Optional.of(stored) : Optional.empty();
    }

    @Override
    public synchronized Optional<StoredProfile> update(CareerProfile profile, long expectedVersion) {
        StoredProfile current = profiles.get(profile.owner());
        if (current == null || current.version() != expectedVersion) {
            return Optional.empty();
        }
        StoredProfile stored = new StoredProfile(profile, expectedVersion + 1);
        profiles.put(profile.owner(), stored);
        return Optional.of(stored);
    }

    @Override
    public boolean delete(UserId owner) {
        return profiles.remove(owner) != null;
    }

    public void clear() {
        profiles.clear();
    }
}
