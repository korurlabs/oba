-- Database Schema - Replace attachment exists-check index
-- V1_2_54__Shrine_Replace_Attachment_Exists_Index.sql
--
-- The /storage/attachments/exists endpoint and the upload-time duplicate
-- guard now look up by (entity_type, file_hash) instead of
-- (entity_type, entity_id, file_hash). Replace the supporting composite
-- index accordingly. The entity_id column is NOT dropped — it is still
-- used by the list query (findByEntityTypeAndEntityId) and stored on
-- every uploaded attachment.

-- =====================================================
-- 1. Drop the old composite index
-- =====================================================

DROP INDEX IF EXISTS shrine.shrine_attachments_entity_type_entity_id_file_hash_idx;

-- =====================================================
-- 2. Create the new two-column index
-- =====================================================

CREATE INDEX shrine_attachments_entity_type_file_hash_idx
    ON shrine.attachments (entity_type, file_hash);
