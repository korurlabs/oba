-- Oba Database Schema - Shrine: Create Groups And App Grants
-- V1_2_111__Shrine_Create_Groups_And_App_Grants.sql
--
-- An organization grants each app (KAIZEN, KAYA, KALKAN, OTAK) to users directly or through
-- groups. Every organization has one EVERYONE group that stands for all of its
-- members; it has no member rows. A CUSTOM group lists its members in
-- shrine.organization_group_members.
--
-- Soft delete (deleted = TRUE) is the only way the application removes a row, so
-- every unique index is partial on deleted = FALSE and a revoked grant or a left
-- group can be created again.
--
-- Delete rules (as shrine.organization_members, which cascades from organizations
-- and users):
--   organization_groups.organization_id        ON DELETE CASCADE: deleting an organization deletes its groups.
--   organization_group_members.group_id        ON DELETE CASCADE: deleting a group deletes its member rows.
--   organization_group_members.user_id         ON DELETE CASCADE: deleting a user deletes the memberships.
--   organization_app_grants.organization_id    ON DELETE CASCADE: deleting an organization deletes its grants.
--   organization_app_grants.user_id            ON DELETE CASCADE: deleting a user deletes the grants made to the user.
--   organization_app_grants.group_id           ON DELETE CASCADE: deleting a group deletes the grants made to it.
--   organization_app_grants.granted_by_user_id ON DELETE SET NULL: the grant outlives the user who made it.
--
-- Backfill: one EVERYONE group named 'Everyone' per organization, and a grant of
-- every app to it, so every current member keeps every app.

-- =====================================================
-- 1. Types
-- =====================================================

CREATE TYPE shrine_app_grant_app_enum AS ENUM ('KAIZEN', 'KAYA', 'KALKAN', 'OTAK');
CREATE TYPE shrine_organization_group_type_enum AS ENUM ('EVERYONE', 'CUSTOM');

-- =====================================================
-- 2. Groups
-- =====================================================

CREATE TABLE shrine.organization_groups (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    type shrine_organization_group_type_enum NOT NULL DEFAULT 'CUSTOM',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organization_groups_pk PRIMARY KEY (id),
    CONSTRAINT organization_groups_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- A name is unique in an organization, whatever its case. The same index serves
-- the foreign key lookup by organization_id.
CREATE UNIQUE INDEX organization_groups_organization_id_name_uk
    ON shrine.organization_groups (organization_id, lower(name))
    WHERE deleted = FALSE;

-- At most one EVERYONE group per organization.
CREATE UNIQUE INDEX organization_groups_organization_id_everyone_uk
    ON shrine.organization_groups (organization_id)
    WHERE type = 'EVERYONE' AND deleted = FALSE;

-- =====================================================
-- 3. Group members (CUSTOM groups only)
-- =====================================================

CREATE TABLE shrine.organization_group_members (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    user_id UUID NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organization_group_members_pk PRIMARY KEY (id),
    CONSTRAINT organization_group_members_group_id_fk FOREIGN KEY (group_id) REFERENCES shrine.organization_groups (id) ON DELETE CASCADE,
    CONSTRAINT organization_group_members_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

-- A user is in a group once. Leading column group_id also serves the group's member list.
CREATE UNIQUE INDEX organization_group_members_group_id_user_id_uk
    ON shrine.organization_group_members (group_id, user_id)
    WHERE deleted = FALSE;

-- The access check asks for the groups of a user; the user_id foreign key needs it too.
CREATE INDEX organization_group_members_user_id_idx
    ON shrine.organization_group_members (user_id);

-- =====================================================
-- 4. App grants
-- =====================================================

CREATE TABLE shrine.organization_app_grants (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    app shrine_app_grant_app_enum NOT NULL,
    user_id UUID NULL,
    group_id UUID NULL,
    granted_by_user_id UUID NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organization_app_grants_pk PRIMARY KEY (id),
    CONSTRAINT organization_app_grants_target_ck CHECK (num_nonnulls(user_id, group_id) = 1),
    CONSTRAINT organization_app_grants_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT organization_app_grants_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE,
    CONSTRAINT organization_app_grants_group_id_fk FOREIGN KEY (group_id) REFERENCES shrine.organization_groups (id) ON DELETE CASCADE,
    CONSTRAINT organization_app_grants_granted_by_user_id_fk FOREIGN KEY (granted_by_user_id) REFERENCES shrine.users (id) ON DELETE SET NULL
);

-- One live grant of an app per user in an organization. Its leading column also
-- serves the organization_id foreign key and the organization's grant list.
CREATE UNIQUE INDEX organization_app_grants_organization_id_app_user_id_uk
    ON shrine.organization_app_grants (organization_id, app, user_id)
    WHERE user_id IS NOT NULL AND deleted = FALSE;

-- One live grant of an app per group. Its leading column also serves the group_id foreign key.
CREATE UNIQUE INDEX organization_app_grants_group_id_app_uk
    ON shrine.organization_app_grants (group_id, app)
    WHERE group_id IS NOT NULL AND deleted = FALSE;

-- The access check asks for the grants of a user; the user_id foreign key needs it too.
CREATE INDEX organization_app_grants_user_id_idx
    ON shrine.organization_app_grants (user_id);

-- Foreign key index: deleting a user sets granted_by_user_id to NULL.
CREATE INDEX organization_app_grants_granted_by_user_id_idx
    ON shrine.organization_app_grants (granted_by_user_id);

-- =====================================================
-- 5. Backfill
-- =====================================================

INSERT INTO shrine.organization_groups (organization_id, name, type)
SELECT o.id, 'Everyone', 'EVERYONE'
FROM shrine.organizations o;

INSERT INTO shrine.organization_app_grants (organization_id, app, group_id, granted_by_user_id)
SELECT g.organization_id, a.app, g.id, NULL
FROM shrine.organization_groups g
CROSS JOIN (SELECT unnest(enum_range(NULL::shrine_app_grant_app_enum)) AS app) a
WHERE g.type = 'EVERYONE';
