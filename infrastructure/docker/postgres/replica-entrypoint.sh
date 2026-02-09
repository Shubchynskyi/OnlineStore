#!/bin/bash
set -euo pipefail

DATA_DIR="/var/lib/postgresql/data"
PRIMARY_HOST="${POSTGRES_PRIMARY_HOST:-postgres-primary}"
PRIMARY_PORT="${POSTGRES_PRIMARY_PORT:-5432}"
REPL_USER="${POSTGRES_REPLICATION_USER:-replicator}"
REPL_PASSWORD="${POSTGRES_REPLICATION_PASSWORD:-replicator_password}"
REPL_SLOT="${POSTGRES_REPLICATION_SLOT:-replica1_slot}"

if [ ! -f "$DATA_DIR/PG_VERSION" ]; then
  echo "Waiting for primary to be ready..."
  until pg_isready -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "${POSTGRES_USER:-store}"; do
    sleep 2
  done

  echo "Running base backup from primary using slot '$REPL_SLOT'..."
  export PGPASSWORD="$REPL_PASSWORD"
  pg_basebackup -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -D "$DATA_DIR" \
    -U "$REPL_USER" -v -P --wal-method=stream --slot="$REPL_SLOT"

  # Configure streaming replication with slot
  cat >> "$DATA_DIR/postgresql.auto.conf" <<EOF
primary_conninfo = 'host=$PRIMARY_HOST port=$PRIMARY_PORT user=$REPL_USER password=$REPL_PASSWORD application_name=replica1'
primary_slot_name = '$REPL_SLOT'
EOF

  # Create standby signal file
  touch "$DATA_DIR/standby.signal"

  # Fix ownership and permissions for postgres user
  chown -R postgres:postgres "$DATA_DIR"
  chmod 700 "$DATA_DIR"

  echo "Replica initialized successfully as standby"
fi

# Run postgres as postgres user
exec gosu postgres postgres -c config_file=/etc/postgresql/postgresql.conf
