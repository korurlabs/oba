-- Village Database Schema - Iroh Change Journal Entry Source Id To Uuid
-- V1_2_16__Iroh_Change_Journal_Entry_Source_Id_To_Uuid.sql
--
-- Changes journal_entries.source_id from BIGINT to UUID.
--
-- Why this migration is safe:
-- Invoice PKs are UUIDs. The source_id column was originally typed BIGINT due to a legacy
-- assumption when the journal entry entity was first created, before invoice PKs were
-- confirmed to be UUID. The invoice-journal-proposal feature requires linking a journal entry
-- back to the originating invoice, which is impossible with a BIGINT column holding a UUID key.
--
-- No data is lost: source_id has never held any values in this codebase. The invoice-linking
-- feature (populating source_id) was never completed before this migration. All existing rows
-- have source_id = NULL, so USING NULL is a safe coercion strategy.
--
-- The composite index on (source_type, source_id) must be dropped before the ALTER COLUMN
-- because PostgreSQL cannot change the type of an indexed column in-place. It is recreated
-- with the same name and partial-index predicate after the type change, so existing query
-- plans that reference this index remain valid.

-- =====================================================
-- 1. Drop dependent index
-- =====================================================

-- Drop the composite index that depends on source_id before altering the column type.
-- The index was created in V1_2_6 at lines 281-283 with this exact name.
DROP INDEX IF EXISTS iroh.journal_entries_source_type_source_id_idx;

-- =====================================================
-- 2. Change column type
-- =====================================================

-- Change source_id from BIGINT to UUID. USING NULL is correct here: all existing rows
-- have source_id = NULL (the column was never populated), so no data conversion is needed.
ALTER TABLE iroh.journal_entries
    ALTER COLUMN source_id TYPE UUID USING NULL;

-- =====================================================
-- 3. Recreate index
-- =====================================================

-- Recreate the composite index with the same name as in V1_2_6 so existing query plans
-- for source-link lookups (find all entries from a given invoice) remain valid.
CREATE INDEX journal_entries_source_type_source_id_idx
    ON iroh.journal_entries(source_type, source_id)
    WHERE source_id IS NOT NULL;
