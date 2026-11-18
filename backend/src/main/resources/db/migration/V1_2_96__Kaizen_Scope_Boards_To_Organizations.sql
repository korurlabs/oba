-- Oba Database Schema - Kaizen: Scope Boards To Organizations
-- V1_2_96__Kaizen_Scope_Boards_To_Organizations.sql
--
-- A board belongs to exactly one organization, and every board in a tree belongs
-- to the same one. This migration adds kaizen.boards.organization_id, assigns an
-- organization to every existing board, and makes the rule a constraint.
--
-- Backfill of root boards (parent_board_id IS NULL, live and soft-deleted), first
-- rule that matches:
--   (a) the organization whose kaizen.organization_settings.security_board_id or
--       shrine.organizations.security_board_id is the board, oldest organization first;
--   (b) the live organization with the most live shrine.organization_members rows
--       among the board's live kaizen.board_members; ties go to the organization of
--       the board's OWNER member, then to the oldest organization;
--   (c) the oldest live organization (the board ids are reported with RAISE NOTICE).
-- A board that no rule can place fails the migration (RAISE EXCEPTION) when no
-- organization exists at all, instead of leaving a board without an owner.
-- Descendants take the organization of their root.
--
-- Security board references that point at a board of another organization are
-- cleared, so an organization never keeps a security board it cannot open.
-- Board member rows of users outside the board's organization are kept: the
-- request-time organization check blocks them, and deleting them here would lose
-- the membership history.
--
-- Delete rules:
--   boards_organization_id_fk ON DELETE CASCADE: deleting an organization deletes
--     its boards, and through the existing board foreign keys everything under them.
--   boards_parent_board_id_organization_id_fk (default NO ACTION): refuses a child
--     whose organization differs from its parent's. Deleting a parent is still
--     handled by boards_parent_board_id_fk (ON DELETE SET NULL), which runs first.

-- =====================================================
-- 1. Add the column
-- =====================================================

ALTER TABLE kaizen.boards ADD COLUMN organization_id UUID NULL;

-- =====================================================
-- 2. Backfill root boards
-- =====================================================

CREATE TEMPORARY TABLE board_organization_assignment (
    board_id UUID NOT NULL PRIMARY KEY,
    organization_id UUID NOT NULL,
    rule CHAR(1) NOT NULL
) ON COMMIT DROP;

-- Rule (a): the board is the security board of an organization.
INSERT INTO board_organization_assignment (board_id, organization_id, rule)
SELECT DISTINCT ON (b.id) b.id, o.id, 'a'
FROM kaizen.boards b
JOIN (
    SELECT security_board_id AS board_id, organization_id FROM kaizen.organization_settings WHERE security_board_id IS NOT NULL
    UNION
    SELECT security_board_id AS board_id, id AS organization_id FROM shrine.organizations WHERE security_board_id IS NOT NULL
) s ON s.board_id = b.id
JOIN shrine.organizations o ON o.id = s.organization_id
WHERE b.parent_board_id IS NULL
ORDER BY b.id, o.created_at ASC, o.id ASC;

-- Rule (b): the live organization most of the board's live members belong to.
INSERT INTO board_organization_assignment (board_id, organization_id, rule)
SELECT DISTINCT ON (bm.board_id) bm.board_id, om.organization_id, 'b'
FROM kaizen.board_members bm
JOIN kaizen.boards b ON b.id = bm.board_id AND b.parent_board_id IS NULL
JOIN shrine.organization_members om ON om.user_id = bm.user_id AND om.deleted = FALSE
JOIN shrine.organizations o ON o.id = om.organization_id AND o.deleted = FALSE
WHERE bm.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM board_organization_assignment a WHERE a.board_id = bm.board_id)
GROUP BY bm.board_id, om.organization_id, o.created_at, o.id
ORDER BY bm.board_id,
         COUNT(*) DESC,
         BOOL_OR(bm.role::text = 'OWNER') DESC,
         o.created_at ASC,
         o.id ASC;

-- Rule (c): the oldest live organization; a soft-deleted one only if none is live.
DO $$
DECLARE
    fallback_organization_id UUID;
    fallen_board_ids TEXT;
BEGIN
    SELECT id INTO fallback_organization_id
    FROM shrine.organizations
    ORDER BY deleted ASC, created_at ASC, id ASC
    LIMIT 1;

    SELECT string_agg(b.id::text, ', ' ORDER BY b.created_at, b.id) INTO fallen_board_ids
    FROM kaizen.boards b
    WHERE b.parent_board_id IS NULL
      AND NOT EXISTS (SELECT 1 FROM board_organization_assignment a WHERE a.board_id = b.id);

    IF fallen_board_ids IS NOT NULL THEN
        IF fallback_organization_id IS NULL THEN
            RAISE EXCEPTION 'kaizen.boards has root boards but shrine.organizations is empty, cannot assign organization_id: %', fallen_board_ids;
        END IF;

        INSERT INTO board_organization_assignment (board_id, organization_id, rule)
        SELECT b.id, fallback_organization_id, 'c'
        FROM kaizen.boards b
        WHERE b.parent_board_id IS NULL
          AND NOT EXISTS (SELECT 1 FROM board_organization_assignment a WHERE a.board_id = b.id);

        RAISE NOTICE 'Boards assigned to the oldest organization % by rule (c): %', fallback_organization_id, fallen_board_ids;
    END IF;
END $$;

UPDATE kaizen.boards b
SET organization_id = a.organization_id
FROM board_organization_assignment a
WHERE a.board_id = b.id;

-- =====================================================
-- 3. Descendants take their root's organization
-- =====================================================

WITH RECURSIVE tree AS (
    SELECT id, organization_id
    FROM kaizen.boards
    WHERE parent_board_id IS NULL
    UNION ALL
    SELECT child.id, tree.organization_id
    FROM kaizen.boards child
    JOIN tree ON child.parent_board_id = tree.id
)
UPDATE kaizen.boards b
SET organization_id = tree.organization_id
FROM tree
WHERE tree.id = b.id
  AND b.parent_board_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM kaizen.boards WHERE organization_id IS NULL) THEN
        RAISE EXCEPTION 'kaizen.boards has boards that no root reaches (a parent cycle), cannot assign organization_id';
    END IF;
END $$;

-- =====================================================
-- 4. Clear security board references that cross organizations
-- =====================================================

UPDATE kaizen.organization_settings s
SET security_board_id = NULL
FROM kaizen.boards b
WHERE b.id = s.security_board_id
  AND b.organization_id <> s.organization_id;

UPDATE shrine.organizations o
SET security_board_id = NULL
FROM kaizen.boards b
WHERE b.id = o.security_board_id
  AND b.organization_id <> o.id;

-- =====================================================
-- 5. Constraints and indexes
-- =====================================================

ALTER TABLE kaizen.boards ALTER COLUMN organization_id SET NOT NULL;

ALTER TABLE kaizen.boards ADD CONSTRAINT boards_organization_id_fk
    FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE;

-- Every board list is filtered by the request's organization, and the foreign key
-- needs the index when an organization is deleted.
CREATE INDEX boards_organization_id_idx ON kaizen.boards (organization_id);

-- Target of the composite foreign key below; id alone is already unique.
ALTER TABLE kaizen.boards ADD CONSTRAINT boards_id_organization_id_uk UNIQUE (id, organization_id);

-- A child board stays in its parent's organization. MATCH SIMPLE skips root boards
-- (parent_board_id IS NULL). The leading column parent_board_id is covered by
-- boards_parent_board_id_idx.
ALTER TABLE kaizen.boards ADD CONSTRAINT boards_parent_board_id_organization_id_fk
    FOREIGN KEY (parent_board_id, organization_id) REFERENCES kaizen.boards (id, organization_id);
