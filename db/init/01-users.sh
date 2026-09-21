#!/bin/bash
#
# Runs automatically on the Postgres container's FIRST startup only, because
# it lives under /docker-entrypoint-initdb.d/ (the official postgres image
# executes every .sh/.sql file there once, the first time the data volume is
# created). It sets up dual database user segregation — one of the spec's
# fixed infra decisions (see .claude/docs/specs/memory/infra.md):
#
#   migration: owns the schema, allowed to CREATE/ALTER/DROP tables.
#              Flyway (Task 0.5) connects as this user to run migrations.
#   runtime:   the application's day-to-day user. Can only read/write rows
#              (SELECT/INSERT/UPDATE/DELETE) in tables migration already
#              created — it can never change the schema itself. This limits
#              the blast radius of an application-level bug or SQL injection:
#              even a fully compromised app can't DROP TABLE or ALTER a
#              column, because the DB user it authenticates as was never
#              granted that power.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    -- LOGIN roles are Postgres's equivalent of "user accounts": a role that
    -- can open a connection and authenticate with a password.
    CREATE ROLE migration WITH LOGIN PASSWORD '${MIGRATION_DB_PASSWORD}';
    CREATE ROLE runtime WITH LOGIN PASSWORD '${RUNTIME_DB_PASSWORD}';

    -- migration is the schema owner: full rights, including DDL.
    --
    -- Two separate grants are needed, easy to miss. GRANT ... ON DATABASE
    -- only covers database-level rights (CONNECT, CREATE-a-schema, TEMP)
    -- — it does NOT include the right to create tables inside an existing
    -- schema. That's a SEPARATE schema-level privilege, and since
    -- PostgreSQL 15 it is no longer granted to non-owner roles by default
    -- (this is exactly the hardening that keeps "runtime" powerless later
    -- in this same script). So "migration" needs an explicit schema-level
    -- grant too, or its very first Flyway migration fails with
    -- "permission denied for schema public".
    GRANT ALL PRIVILEGES ON DATABASE ${POSTGRES_DB} TO migration;
    GRANT ALL ON SCHEMA public TO migration;
    -- runtime may only open a connection to the database...
    GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO runtime;
    -- ...and "see into" the public schema (a prerequisite for querying any
    -- table in it — USAGE is not the same as being able to read rows).
    GRANT USAGE ON SCHEMA public TO runtime;

    -- Since PostgreSQL 15, CREATE on the public schema is no longer granted
    -- to PUBLIC by default, so runtime already has zero DDL rights without
    -- us doing anything extra. The line below is the DML counterpart: it
    -- tells Postgres "any table migration creates from now on, grant
    -- runtime SELECT/INSERT/UPDATE/DELETE on it automatically" — so we
    -- never have to remember a manual GRANT every time a new table is
    -- added by a future Flyway migration.
    ALTER DEFAULT PRIVILEGES FOR ROLE migration IN SCHEMA public
      GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO runtime;
SQL
