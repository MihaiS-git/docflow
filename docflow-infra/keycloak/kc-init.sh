#!/usr/bin/env sh
set -eu

KC_BASE_INTERNAL="http://docflow-keycloak:8080"
REALM="docflow"

ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:?missing KC_BOOTSTRAP_ADMIN_USERNAME}"
ADMIN_PASS="${KC_BOOTSTRAP_ADMIN_PASSWORD:?missing KC_BOOTSTRAP_ADMIN_PASSWORD}"

CLIENT_ID="docflow-admin-events"

echo "[kc-init] waiting for keycloak at ${KC_BASE_INTERNAL} ..."
until curl -fsS "${KC_BASE_INTERNAL}/realms/master" >/dev/null 2>&1; do
  sleep 2
done

echo "[kc-init] requesting admin token ..."
TOKEN="$(
  curl -fsS -X POST "${KC_BASE_INTERNAL}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=admin-cli" \
    --data-urlencode "username=${ADMIN_USER}" \
    --data-urlencode "password=${ADMIN_PASS}" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
)"

if [ -z "${TOKEN}" ]; then
  echo "[kc-init] ERROR: cannot obtain admin token"
  exit 1
fi

authz() {
  curl -fsS -H "Authorization: Bearer ${TOKEN}" "$@"
}

echo "[kc-init] resolving client uuid for ${CLIENT_ID} in realm ${REALM} ..."
CLIENT_UUID="$(
  authz "${KC_BASE_INTERNAL}/admin/realms/${REALM}/clients?clientId=${CLIENT_ID}" \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' \
  | head -n 1
)"

if [ -z "${CLIENT_UUID}" ]; then
  echo "[kc-init] ERROR: client '${CLIENT_ID}' not found in realm '${REALM}'"
  exit 1
fi

echo "[kc-init] resolving service account user id ..."
SA_USER_ID="$(
  authz "${KC_BASE_INTERNAL}/admin/realms/${REALM}/clients/${CLIENT_UUID}/service-account-user" \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' \
  | head -n 1
)"

if [ -z "${SA_USER_ID}" ]; then
  echo "[kc-init] ERROR: cannot resolve service-account user for client '${CLIENT_ID}'"
  exit 1
fi

echo "[kc-init] resolving realm-management client uuid ..."
RM_CLIENT_UUID="$(
  authz "${KC_BASE_INTERNAL}/admin/realms/${REALM}/clients?clientId=realm-management" \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' \
  | head -n 1
)"

if [ -z "${RM_CLIENT_UUID}" ]; then
  echo "[kc-init] ERROR: realm-management client not found"
  exit 1
fi

# roles you said you need under: Service account roles -> Assign roles -> Client roles -> realm-management
# manage-users, query-users, view-events, view-realm, view-users
ROLE_NAMES="manage-users query-users view-events view-realm view-users"

echo "[kc-init] assigning realm-management roles to service account..."
for ROLE in $ROLE_NAMES; do
  echo "  - role: ${ROLE}"

  ROLE_JSON="$(
    authz "${KC_BASE_INTERNAL}/admin/realms/${REALM}/clients/${RM_CLIENT_UUID}/roles/${ROLE}"
  )"

  # assign (idempotent: Keycloak will ignore duplicates or return 409 depending on version; we treat non-2xx as fatal)
  curl -fsS -X POST "${KC_BASE_INTERNAL}/admin/realms/${REALM}/users/${SA_USER_ID}/role-mappings/clients/${RM_CLIENT_UUID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "[${ROLE_JSON}]"
done

echo "[kc-init] OK: service account roles assigned"
