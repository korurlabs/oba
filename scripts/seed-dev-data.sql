-- Oba local development seed data
-- Creates a default organization for local development, with its EVERYONE group
-- and a grant of every app to that group.
-- Users are auto-created by the backend on first login via Keycloak.
-- After login, run: UPDATE shrine.users SET default_organization_id = 'b0000000-...' WHERE ...

DO $$
DECLARE
    v_org_id UUID := 'b0000000-0000-0000-0000-000000000001';
BEGIN
    -- Skip if organization already exists (idempotent)
    IF EXISTS (SELECT 1 FROM shrine.organizations WHERE id = v_org_id) THEN
        RAISE NOTICE 'Organization already exists, skipping seed data';
        RETURN;
    END IF;

    -- Create default organization
    INSERT INTO shrine.organizations (id, name, industry, country, status)
    VALUES (v_org_id, 'Sensey', 'Technology', 'NL', 'ACTIVE');

    RAISE NOTICE 'Seed data created: org=Sensey';
END $$;

-- A member opens an app only through a grant, and an organization grants every
-- app to its EVERYONE group by default. The backfill in
-- V1_2_111__Shrine_Create_Groups_And_App_Grants.sql writes these rows for the
-- organizations that exist when it runs; this seed runs after the migrations, so
-- it writes the same rows for its own organization. Each statement skips the
-- rows that exist, so these statements also complete a database seeded before
-- the groups existed.

INSERT INTO shrine.organization_groups (organization_id, name, type)
SELECT o.id, 'Everyone', 'EVERYONE'
FROM shrine.organizations o
WHERE o.id = 'b0000000-0000-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1
      FROM shrine.organization_groups g
      WHERE g.organization_id = o.id
        AND g.type = 'EVERYONE'
        AND g.deleted = FALSE
  );

INSERT INTO shrine.organization_app_grants (organization_id, app, group_id, granted_by_user_id)
SELECT g.organization_id, a.app, g.id, NULL
FROM shrine.organization_groups g
CROSS JOIN (SELECT unnest(enum_range(NULL::shrine_app_grant_app_enum)) AS app) a
WHERE g.organization_id = 'b0000000-0000-0000-0000-000000000001'
  AND g.type = 'EVERYONE'
  AND g.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM shrine.organization_app_grants x
      WHERE x.group_id = g.id
        AND x.app = a.app
        AND x.deleted = FALSE
  );
