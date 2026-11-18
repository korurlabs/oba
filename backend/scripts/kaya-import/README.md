# Kaya data import

One-off import of the Kaya production data into schema `kaya` of the Oba database (cutover step K4). Run it once, after Kaya is frozen (K2) and the backend with the Kaya migrations is deployed (K3), so that Flyway has created schema `kaya` (`V1_2_85`, `V1_2_86`). The script writes no Flyway history and never touches `public.flyway_schema_history`.

The database must be at `V1_2_92` or lower when the import runs. The script writes the agent-named tables and columns (`kaya.agents`, `kaya.agent_tokens`, `kaya.agent_usage_snapshots`, `agent_id`), and the restore of step 3 needs the agent-named enum types and audit actions; `V1_2_93` renames all of them to machine. Deploy the backend with `V1_2_93` only after the real run: it then renames the imported rows with the rest of the Kaya data.

## What it does

Kaya's own users, organizations and memberships are not copied. They are mapped onto the platform tables, and every user and organization reference in the copied rows is rewritten to the platform id.

- Users: matched by `lower(email)` to a non-deleted `shrine.users` row (the oldest when several match). Without a match, a new `shrine.users` row is created with the lower-cased Kaya email, `keycloak_id` NULL, the Kaya status (`ACTIVE` or `DISABLED`) and the display name split into first and last name. The first sign-in through realm `sensey` links the Keycloak account by email. Several Kaya users with the same email share one platform user.
- Organizations: `kaya_import.organization_map(kaya_organization_id, platform_organization_id)`. Rows you insert before the run are kept. Each Kaya organization without a row is matched by `lower(name)` to a non-deleted `shrine.organizations` row (Kaya `Korur` to the platform Korur), else a new organization is created (`ACTIVE`; a suspended Kaya organization becomes `DISABLED`). `retention_days` goes to `kaya.organization_settings`.
- Memberships: copied to `shrine.organization_members` with the same role and status. An existing membership keeps the higher role and its own status; a Kaya `DISABLED` membership is created `DISABLED`.
- Not copied: `identity.organization_email_domains` (people join by invitation) and `identity.organization_audit_entries` (the table no longer exists in `kaya`).
- Every other Kaya table is copied row for row into `kaya.<table>` with ids and timestamps unchanged. `packs.current_version_id` is set after `versions` is copied because the two tables reference each other.

The maps stay behind in schema `kaya_import` (`organization_map`, `user_map`) as the record of what was mapped to what. Drop the schema when the cutover is finished.

## Safety

The script runs in one transaction and stops at the first error, so a failure writes nothing. It refuses to start when a source table is missing or when any `kaya` table already holds rows, so a second real run changes nothing. It commits only with `-v commit=1`; without it, or with any other value such as `-v commit=0`, it rolls back at the end (dry run). Before the end it compares the row count of every source table with its `kaya` table and fails on a difference.

## Steps

Use a role that owns the Oba database objects (the migration role). `$KAYA_URL` and `$OBA_URL` are connection strings of the two databases.

1. Back up both databases. Confirm that the backend has applied `V1_2_85` and `V1_2_86` and not yet `V1_2_93` (`select version from public.flyway_schema_history order by installed_rank desc limit 5;` in Oba).
2. Create the five enum types that only Kaya's identity tables use, so that the restore can create them (the script drops them again on a real run):

   ```sql
   CREATE TYPE public.identity_organization_audit_entry_action_enum AS ENUM ('MEMBER_ADDED', 'MEMBER_ROLE_CHANGED', 'MEMBER_DISABLED', 'MEMBER_ENABLED', 'MEMBER_REMOVED', 'EMAIL_DOMAIN_ADDED', 'EMAIL_DOMAIN_REMOVED');
   CREATE TYPE public.identity_organization_member_status_enum AS ENUM ('ACTIVE', 'DISABLED');
   CREATE TYPE public.identity_organization_role_enum AS ENUM ('OWNER', 'ADMIN', 'MEMBER');
   CREATE TYPE public.identity_organization_status_enum AS ENUM ('ACTIVE', 'SUSPENDED');
   CREATE TYPE public.identity_user_status_enum AS ENUM ('ACTIVE', 'DISABLED');
   ```

3. Restore the Kaya schemas into Oba under their old names. Schema `public` is excluded: it holds only Kaya's Flyway history and its enum types, which `V1_2_85` already created in Oba under the same names.

   ```sh
   pg_dump "$KAYA_URL" --exclude-schema=public --no-owner --format=custom --file=kaya.dump
   pg_restore --dbname="$OBA_URL" --no-owner --exit-on-error kaya.dump
   ```

4. Optional: set organization mappings by hand. Create the map table and insert the rows before the dry run (the script creates the table if it is missing and fills the missing rows itself):

   ```sql
   CREATE SCHEMA IF NOT EXISTS kaya_import;
   CREATE TABLE IF NOT EXISTS kaya_import.organization_map (
       kaya_organization_id UUID NOT NULL PRIMARY KEY,
       platform_organization_id UUID NOT NULL REFERENCES shrine.organizations (id));
   INSERT INTO kaya_import.organization_map VALUES ('<kaya organization id>', '<shrine.organizations id>');
   ```

5. Dry run, then read the `NOTICE` row counts, the user line (matched and created) and the organization line:

   ```sh
   psql "$OBA_URL" -f import.sql
   ```

   It ends with `DRY RUN: rolled back`. Repeat it as often as needed; the restored schemas stay.

6. Real run:

   ```sh
   psql "$OBA_URL" -v commit=1 -f import.sql
   ```

   It ends with `COMMITTED`, and has dropped the restored schemas and the five helper enum types.

7. Verify: the Korur users sign in through Microsoft and see their sessions, plans and rules (cutover K6). The Kaya database stays untouched until K8.

If the real run fails, nothing was written; fix the cause and run again. If it succeeded and must be undone, restore the Oba backup of step 1.

## Assumptions

- A Kaya user whose email matches a platform user that is `DISABLED` or has no membership still maps to it; the membership step adds the membership.
- Two Kaya users that map to one platform user can collide on a unique key of a copied table (for example the same account linked twice); the script then fails and writes nothing, and the rows need a manual decision.
- A platform user created by the import has no default organization and no profile data beyond email, name and status.
