#!/usr/bin/env bash
# Creates the Keycloak database next to the application database.
# Runs only on the first start of an empty data volume.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v kc_password="$KEYCLOAK_DB_PASSWORD" <<-'EOSQL'
	CREATE USER keycloak WITH PASSWORD :'kc_password';
	CREATE DATABASE keycloak OWNER keycloak;
EOSQL
