#!/bin/sh
set -eu

KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:8080}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD_VALUE="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM_NAME="${KEYCLOAK_REALM:-online-store}"
DEFAULT_ROLE_NAME="${KEYCLOAK_DEFAULT_ROLE:-default-roles-online-store}"
TARGET_ROLE_NAME="${KEYCLOAK_TARGET_ROLE:-ROLE_CLIENT}"
MAX_RETRIES="${KEYCLOAK_PROVISION_RETRIES:-120}"
SLEEP_SECONDS="${KEYCLOAK_PROVISION_SLEEP_SECONDS:-2}"

log() {
  echo "[keycloak-provision] $1"
}

wait_for_admin_login() {
  attempt=1
  while [ "$attempt" -le "$MAX_RETRIES" ]; do
    if /opt/keycloak/bin/kcadm.sh config credentials \
      --server "$KEYCLOAK_URL" \
      --realm master \
      --user "$KEYCLOAK_ADMIN_USER" \
      --password "$KEYCLOAK_ADMIN_PASSWORD_VALUE" >/dev/null 2>&1; then
      return 0
    fi

    sleep "$SLEEP_SECONDS"
    attempt=$((attempt + 1))
  done

  return 1
}

wait_for_realm() {
  attempt=1
  while [ "$attempt" -le "$MAX_RETRIES" ]; do
    if /opt/keycloak/bin/kcadm.sh get "realms/$REALM_NAME" >/dev/null 2>&1; then
      return 0
    fi

    sleep "$SLEEP_SECONDS"
    attempt=$((attempt + 1))
  done

  return 1
}

log "Waiting for Keycloak admin API..."
if ! wait_for_admin_login; then
  log "ERROR: Could not authenticate to Keycloak after $MAX_RETRIES attempts"
  exit 1
fi

log "Waiting for realm '$REALM_NAME' to be available..."
if ! wait_for_realm; then
  log "ERROR: Realm '$REALM_NAME' was not found after $MAX_RETRIES attempts"
  exit 1
fi

if ! /opt/keycloak/bin/kcadm.sh get "roles/$TARGET_ROLE_NAME" -r "$REALM_NAME" >/dev/null 2>&1; then
  log "ERROR: Role '$TARGET_ROLE_NAME' does not exist in realm '$REALM_NAME'"
  exit 1
fi

if ! /opt/keycloak/bin/kcadm.sh get "roles/$DEFAULT_ROLE_NAME" -r "$REALM_NAME" >/dev/null 2>&1; then
  log "ERROR: Default role '$DEFAULT_ROLE_NAME' does not exist in realm '$REALM_NAME'"
  exit 1
fi

if /opt/keycloak/bin/kcadm.sh get "roles/$DEFAULT_ROLE_NAME/composites" -r "$REALM_NAME" --fields name --format csv | grep -q "\"$TARGET_ROLE_NAME\""; then
  log "Role '$TARGET_ROLE_NAME' is already assigned to '$DEFAULT_ROLE_NAME'"
  exit 0
fi

log "Assigning role '$TARGET_ROLE_NAME' to '$DEFAULT_ROLE_NAME'..."
/opt/keycloak/bin/kcadm.sh add-roles -r "$REALM_NAME" --rname "$DEFAULT_ROLE_NAME" --rolename "$TARGET_ROLE_NAME"
log "Done"