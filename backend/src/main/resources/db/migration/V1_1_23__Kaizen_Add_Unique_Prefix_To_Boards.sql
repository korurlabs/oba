-- =================================================================
-- Kaizen: Add unique constraint on board prefix
-- =================================================================

-- Step 1: Deduplicate existing prefixes by appending a numeric suffix
-- to all but the oldest board for each duplicate prefix.
UPDATE kaizen.boards AS b
SET prefix = b.prefix || '_' || sub.rn
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY prefix ORDER BY created_at) AS rn
    FROM kaizen.boards
) AS sub
WHERE b.id = sub.id
  AND sub.rn > 1;

-- Step 2: Add the unique constraint now that all prefixes are distinct.
ALTER TABLE kaizen.boards
    ADD CONSTRAINT uq_boards_prefix UNIQUE (prefix);
