# Keycloak Realm Configuration – DocFlow

This directory contains the **authoritative Keycloak realm configuration** for the DocFlow application.

⚠️ **Important**  
The Keycloak **Admin UI is NOT the source of truth**.  
The file `realm-docflow.json` **is**.

If a change is not exported and committed, it **will be lost**.

---

## Source of Truth

- `realm-docflow.json` defines:
  - realm configuration
  - roles (including default roles)
  - clients
  - protocol mappers
  - authentication & registration behavior
- The Keycloak **database is disposable**
- Git is the **only persistent source of configuration**

Running:
docker compose down -v
docker compose up -d
must always recreate the same Keycloak behavior.

Startup Behavior

Keycloak is started with:

--import-realm

This means:
On a fresh database:
the realm is automatically imported from realm-docflow.json
On an existing database:
import is skipped (safe)
Never rely on database persistence for configuration.

Admin UI Usage Rules
The Admin UI may be used only for:
inspecting configuration
experimenting / prototyping
understanding Keycloak behavior
Any UI change is temporary until exported and committed.
If you configure something only in the UI, it will be lost on:
container rebuild
volume removal
host migration

Canonical Realm Export Workflow (Keycloak 26.x)
⚠️ Important
When Keycloak is already running, DO NOT use docker run … export.
You must export from the running container.

Step 1 — Export the realm from the running container
docker exec docflow-keycloak \
 /opt/keycloak/bin/kc.sh export \
 --realm=docflow \
 --dir=/opt/keycloak/data/export \
 --users=skip

--users=skip ensures:
no users
no passwords
no personal data
This is Git-safe.

Step 2 — Inspect the export directory (mandatory)
Keycloak does not guarantee filenames, so always list first:
docker exec docflow-keycloak ls -la /opt/keycloak/data/export
Typical output:
docflow-realm.json

Step 3 — Copy the exported file into the repository

docker cp docflow-keycloak:/opt/keycloak/data/export/docflow-realm.json \
keycloak/

Step 4 — (Optional) Clean up the container
docker exec docflow-keycloak rm -rf /opt/keycloak/data/export

!!! STEP 5
docflow-admin-events -> Manually add roles in Service account roles:
query-users
view-events
view-realm
view-users

Step 6 — Commit the change
git add keycloak/realm-docflow.json
git commit -m "keycloak: export docflow realm configuration"

From this point on, the configuration is safe.
