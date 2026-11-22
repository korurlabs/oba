-- Oba Database Schema - Shrine: Add Organization Id To Attachments
-- V1_2_128__Shrine_Add_Attachment_Organization_Id.sql
--
-- An attachment belongs to an organization, so that its files can be found,
-- counted and removed per organization without joining the owner tables. This
-- migration adds shrine.attachments.organization_id and fills it from the owner
-- of each row:
--   TICKET        kazan.boards.organization_id through kazan.tickets.board_id
--   CHAT_MESSAGE  kazan.chat_messages.organization_id
--   KALKAN_PAGE   kalkan.pages.organization_id
--   MAIL          otak.mails.organization_id
--   RELATION      otak.relations.organization_id
--
-- Orphans: a row whose owner no longer exists (attachments carry no foreign
-- key on entity_id) has no organization. Those rows are deleted, with a RAISE
-- NOTICE of the count per entity type. Their blobs stay in storage.
--
-- Removed owner types: ORGANIZATION, USER and INVOICE are not used by any code.
-- The migration aborts (RAISE EXCEPTION) if a row has one of them, so that no
-- file is deleted or left without an organization silently. The enum
-- shrine_storage_entity_type_enum is then recreated without the three values.
-- shrine.attachments is the only column that uses it (shrine.photos was
-- dropped in V1_2_62).
--
-- Delete rules:
--   attachments_organization_id_fk ON DELETE CASCADE: deleting an organization
--     deletes its attachment rows (not their blobs), as it already deletes the
--     tickets, mails and relations they belong to.
--
-- Indexes:
--   attachments_organization_id_idx serves the organization foreign key and its
--     cascade, and the per organization lookups. Without it a delete of an
--     organization scans the whole table.
--   attachments_entity_type_entity_id_idx (V1_0_6) is rebuilt by PostgreSQL
--     when the column type changes and keeps serving the list of an owner.

-- =====================================================
-- 1. Abort on the owner types that are removed
-- =====================================================

DO $$
DECLARE
    removed_rows BIGINT;
BEGIN
    SELECT count(*) INTO removed_rows
    FROM shrine.attachments
    WHERE entity_type IN ('ORGANIZATION', 'USER', 'INVOICE');

    IF removed_rows > 0 THEN
        RAISE EXCEPTION 'shrine.attachments has % rows of entity_type ORGANIZATION, USER or INVOICE, which cannot be given an organization. Resolve them before this migration.', removed_rows;
    END IF;
END $$;

-- =====================================================
-- 2. Add the column and fill it from the owner
-- =====================================================

ALTER TABLE shrine.attachments ADD COLUMN organization_id UUID;

UPDATE shrine.attachments a
SET organization_id = b.organization_id
FROM kazan.tickets t
JOIN kazan.boards b ON b.id = t.board_id
WHERE a.entity_type = 'TICKET'::shrine_storage_entity_type_enum
  AND a.entity_id = t.id;

UPDATE shrine.attachments a
SET organization_id = m.organization_id
FROM kazan.chat_messages m
WHERE a.entity_type = 'CHAT_MESSAGE'::shrine_storage_entity_type_enum
  AND a.entity_id = m.id;

UPDATE shrine.attachments a
SET organization_id = p.organization_id
FROM kalkan.pages p
WHERE a.entity_type = 'KALKAN_PAGE'::shrine_storage_entity_type_enum
  AND a.entity_id = p.id;

UPDATE shrine.attachments a
SET organization_id = m.organization_id
FROM otak.mails m
WHERE a.entity_type = 'MAIL'::shrine_storage_entity_type_enum
  AND a.entity_id = m.id;

UPDATE shrine.attachments a
SET organization_id = r.organization_id
FROM otak.relations r
WHERE a.entity_type = 'RELATION'::shrine_storage_entity_type_enum
  AND a.entity_id = r.id;

-- =====================================================
-- 3. Delete the orphans, reporting the count per type
-- =====================================================

DO $$
DECLARE
    orphan RECORD;
BEGIN
    FOR orphan IN
        SELECT entity_type::text AS entity_type, count(*) AS orphan_count
        FROM shrine.attachments
        WHERE organization_id IS NULL
        GROUP BY entity_type
        ORDER BY entity_type::text
    LOOP
        RAISE NOTICE 'Deleting % orphan attachments of entity_type %: their owner no longer exists', orphan.orphan_count, orphan.entity_type;
    END LOOP;

    DELETE FROM shrine.attachments WHERE organization_id IS NULL;
END $$;

-- =====================================================
-- 4. Constrain the column
-- =====================================================

ALTER TABLE shrine.attachments ALTER COLUMN organization_id SET NOT NULL;

ALTER TABLE shrine.attachments
    ADD CONSTRAINT attachments_organization_id_fk
    FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE;

CREATE INDEX attachments_organization_id_idx ON shrine.attachments USING btree (organization_id);

-- =====================================================
-- 5. Remove ORGANIZATION, USER and INVOICE from the entity type enum
-- =====================================================

ALTER TYPE shrine_storage_entity_type_enum RENAME TO shrine_storage_entity_type_enum_old;
CREATE TYPE shrine_storage_entity_type_enum AS ENUM ('TICKET', 'CHAT_MESSAGE', 'KALKAN_PAGE', 'MAIL', 'RELATION');
ALTER TABLE shrine.attachments
    ALTER COLUMN entity_type TYPE shrine_storage_entity_type_enum
    USING entity_type::text::shrine_storage_entity_type_enum;
DROP TYPE shrine_storage_entity_type_enum_old;
