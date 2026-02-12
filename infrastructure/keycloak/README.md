# Keycloak Realm Configuration

This directory contains reproducible Keycloak configuration for local development.

## Files

- `realm-export.json` - exported realm model for `online-store`.

## Docker Import

`docker-compose.yml` mounts this directory to `/opt/keycloak/data/import` and starts Keycloak with:

```bash
start-dev --import-realm
```

On startup, Keycloak imports `online-store` from `realm-export.json` if the realm does not already exist in the database.

To refresh the export from a running stack, use:

```bash
task keycloak-export
```

The export is generated with `--users skip`, so realm users are not stored in git.

## Realm

- Realm: `online-store`

## OIDC Clients

- `store-web` - Public, Authorization Code + PKCE (`S256`)
- `admin-panel` - Public, Authorization Code + PKCE (`S256`)
- `mobile-app` - Public, Authorization Code + PKCE (`S256`)
- `backend-service` - Confidential, Client Credentials, secret: `backend-service-secret`
- `telegram-bot` - Confidential, Client Credentials, secret: `telegram-bot-secret`

## Realm Roles

- `ROLE_CLIENT`
- `ROLE_MANAGER`
- `ROLE_ADMIN`
- `ROLE_SUPER_ADMIN`

## Automatic Post-Deploy Provisioning

`docker-compose.yml` runs one-shot service `keycloak-provisioner` on startup.
It executes `infrastructure/keycloak/scripts/provision-default-client-role.sh` and ensures
`ROLE_CLIENT` is added as a composite role of `default-roles-online-store` in realm `online-store`.

The script is idempotent and safe to run on every deploy.
