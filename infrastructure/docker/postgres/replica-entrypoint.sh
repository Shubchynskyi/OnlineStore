#!/bin/bash
set -euo pipefail

DATA_DIR="/var/lib/postgresql/data"
PRIMARY_HOST="${POSTGRES_PRIMARY_HOST:-postgres-primary}"
PRIMARY_PORT="${POSTGRES_PRIMARY_PORT:-5432}"
REPL_USER="${POSTGRES_REPLICATION_USER:-replicator}"
REPL_PASSWORD="${POSTGRES_REPLICATION_PASSWORD:-replicator_password}"

if [ ! -f "$DATA_DIR/PG_VERSION" ]; then
  echo "Waiting for primary to be ready..."
  until pg_isready -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "${POSTGRES_USER:-store}"; do
    sleep 2
  done

  echo "Running base backup from primary..."
  export PGPASSWORD="$REPL_PASSWORD"
  pg_basebackup -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -D "$DATA_DIR" -U "$REPL_USER" -v -P --wal-method=stream

  echo "primary_conninfo = 'host=$PRIMARY_HOST port=$PRIMARY_PORT user=$REPL_USER password=$REPL_PASSWORD application_name=replica1'" >> "$DATA_DIR/postgresql.auto.conf"
  touch "$DATA_DIR/standby.signal"

  # Fix ownership and permissions for postgres user
  chown -R postgres:postgres "$DATA_DIR"
  chmod 700 "$DATA_DIR"
fi

# Run postgres as postgres user
exec gosu postgres postgres -c config_file=/etc/postgresql/postgresql.conf
