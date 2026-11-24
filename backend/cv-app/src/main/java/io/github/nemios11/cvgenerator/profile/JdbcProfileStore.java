package io.github.nemios11.cvgenerator.profile;

import io.github.nemios11.cvgenerator.domain.UserId;
import io.github.nemios11.cvgenerator.domain.profile.CareerProfile;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores each profile as a JSONB document keyed by its owner. Every statement
 * filters on {@code owner_id}; optimistic locking uses the {@code version} column.
 */
@Repository
public class JdbcProfileStore implements ProfileStore {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcProfileStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredProfile> find(UserId owner) {
        return jdbc.sql("SELECT document, schema_version, version FROM career_profile WHERE owner_id = :owner")
                .param("owner", owner.value())
                .query((rs, row) -> read(owner, rs.getString("document"), rs.getInt("schema_version"),
                        rs.getLong("version")))
                .optional();
    }

    @Override
    @Transactional
    public Optional<StoredProfile> insert(CareerProfile profile) {
        int rows = jdbc.sql("""
                        INSERT INTO career_profile (owner_id, document, schema_version, version)
                        VALUES (:owner, CAST(:document AS JSONB), :schema, 0)
                        ON CONFLICT (owner_id) DO NOTHING""")
                .param("owner", profile.owner().value())
                .param("document", write(profile))
                .param("schema", ProfileDto.SCHEMA_VERSION)
                .update();
        return rows == 1 ? Optional.of(new StoredProfile(profile, 0)) : Optional.empty();
    }

    @Override
    @Transactional
    public Optional<StoredProfile> update(CareerProfile profile, long expectedVersion) {
        int rows = jdbc.sql("""
                        UPDATE career_profile
                           SET document = CAST(:document AS JSONB), schema_version = :schema,
                               version = version + 1, updated_at = now()
                         WHERE owner_id = :owner AND version = :expected""")
                .param("owner", profile.owner().value())
                .param("document", write(profile))
                .param("schema", ProfileDto.SCHEMA_VERSION)
                .param("expected", expectedVersion)
                .update();
        return rows == 1 ? Optional.of(new StoredProfile(profile, expectedVersion + 1)) : Optional.empty();
    }

    @Override
    @Transactional
    public boolean delete(UserId owner) {
        return jdbc.sql("DELETE FROM career_profile WHERE owner_id = :owner")
                .param("owner", owner.value())
                .update() == 1;
    }

    private String write(CareerProfile profile) {
        return json.writeValueAsString(ProfileMapper.toDto(profile));
    }

    private StoredProfile read(UserId owner, String document, int schemaVersion, long version) {
        if (schemaVersion != ProfileDto.SCHEMA_VERSION) {
            throw new IllegalStateException("unsupported profile schema version " + schemaVersion);
        }
        ProfileDto dto = json.readValue(document, ProfileDto.class);
        return new StoredProfile(ProfileMapper.toDomain(owner, dto), version);
    }
}
