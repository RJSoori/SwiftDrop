#!/bin/sh
# Runs once on an empty data volume (after the PostGIS image's own init).
# Creates one login and one schema per service; each service only sees its own schema.
set -e

create_service_db() { # login schema password
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v login="$1" -v schema="$2" -v pw="$3" <<'SQL'
CREATE USER :"login" WITH PASSWORD :'pw';
CREATE SCHEMA :"schema" AUTHORIZATION :"login";
ALTER ROLE :"login" SET search_path = :"schema", public;
SQL
}

create_service_db auth_svc auth "$AUTH_DB_PASSWORD"
create_service_db parcel_svc parcel "$PARCEL_DB_PASSWORD"
create_service_db dispatch_svc dispatch "$DISPATCH_DB_PASSWORD"
create_service_db merchant_svc merchant "$MERCHANT_DB_PASSWORD"
create_service_db notification_svc notification "$NOTIFICATION_DB_PASSWORD"
