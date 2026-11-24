-- One career profile per user, stored as a JSON document.
-- The profile is an aggregate that is always read and written as a whole, so a
-- document column keeps the schema simple while the domain model evolves.
-- owner_id is the OIDC subject: it is both the primary key and the isolation key.
CREATE TABLE career_profile (
    owner_id       VARCHAR(255) PRIMARY KEY,
    document       JSONB        NOT NULL,
    schema_version INTEGER      NOT NULL,
    version        BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
