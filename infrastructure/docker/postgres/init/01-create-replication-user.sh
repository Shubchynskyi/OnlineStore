#!/bin/bash
set -euo pipefail

REPL_USER="${POSTGRES_REPLICATION_USER:-replicator}"
REPL_PASS="${POSTGRES_REPLICATION_PASSWORD:-replicator_password}"
REPL_SLOT="${POSTGRES_REPLICATION_SLOT:-replica1_slot}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
-- Create replication user
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$REPL_USER') THEN
    CREATE ROLE $REPL_USER WITH REPLICATION LOGIN PASSWORD '$REPL_PASS';
  END IF;
END
\$\$;

-- Create replication slot for replica
SELECT pg_create_physical_replication_slot('$REPL_SLOT', true)
WHERE NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = '$REPL_SLOT');
EOSQL

echo "Replication user '$REPL_USER' and slot '$REPL_SLOT' created or already exist."
