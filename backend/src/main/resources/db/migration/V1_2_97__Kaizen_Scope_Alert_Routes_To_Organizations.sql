-- Oba Database Schema - Kaizen: Scope Alert Routes To Organizations
-- V1_2_97__Kaizen_Scope_Alert_Routes_To_Organizations.sql
--
-- An alert route maps a system to a board, and a board belongs to one
-- organization. This migration adds kaizen.alert_routes.organization_id, fills
-- it from the route's board, and makes the system key and the default route
-- unique per organization instead of globally. The old constraints were global,
-- so no existing row can violate the per-organization ones.
--
-- Delete rules:
--   alert_routes_organization_id_fk ON DELETE CASCADE: deleting an organization
--     deletes its routes.
--   alert_routes_board_id_organization_id_fk ON DELETE CASCADE: a purged board
--     still removes its routes, and a route can never point to a board of
--     another organization.
--
-- Indexes: alert_routes_organization_id_system_key_uk leads with organization_id,
-- so it also serves lookups and the cascade of the organization foreign key; no
-- separate index is added. alert_routes_board_id_idx stays for the board lookup
-- and the board cascade.

-- =====================================================
-- 1. Add the column and backfill from the board
-- =====================================================

ALTER TABLE kaizen.alert_routes ADD COLUMN organization_id UUID NULL;

UPDATE kaizen.alert_routes ar
SET organization_id = b.organization_id
FROM kaizen.boards b
WHERE b.id = ar.board_id;

DO $$
DECLARE
    unresolved INTEGER;
BEGIN
    SELECT COUNT(*) INTO unresolved FROM kaizen.alert_routes WHERE organization_id IS NULL;
    IF unresolved > 0 THEN
        RAISE EXCEPTION 'V1_2_97: % alert route(s) have no organization after the backfill', unresolved;
    END IF;
END
$$;

-- =====================================================
-- 2. Constraints
-- =====================================================

ALTER TABLE kaizen.alert_routes ALTER COLUMN organization_id SET NOT NULL;

ALTER TABLE kaizen.alert_routes
    ADD CONSTRAINT alert_routes_organization_id_fk
    FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE;

ALTER TABLE kaizen.alert_routes
    ADD CONSTRAINT alert_routes_board_id_organization_id_fk
    FOREIGN KEY (board_id, organization_id) REFERENCES kaizen.boards (id, organization_id) ON DELETE CASCADE;

-- =====================================================
-- 3. Per-organization uniqueness
-- =====================================================

ALTER TABLE kaizen.alert_routes DROP CONSTRAINT alert_routes_system_key_uk;
ALTER TABLE kaizen.alert_routes
    ADD CONSTRAINT alert_routes_organization_id_system_key_uk UNIQUE (organization_id, system_key);

-- At most one default route per organization.
DROP INDEX kaizen.alert_routes_is_default_uk;
CREATE UNIQUE INDEX alert_routes_organization_id_is_default_uk
    ON kaizen.alert_routes (organization_id) WHERE is_default;
