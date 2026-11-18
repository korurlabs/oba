-- Village Database Schema - Rename AI Schema To Sage
-- V1_2_51__Rename_AI_Schema_To_Sage.sql
--
-- Renames the `ai` schema back to `sage` as part of the apps.ai → apps.sage
-- hostname/branding revert. This is the inverse of V1_2_9__Rename_Sage_Schema_To_AI.sql.
-- Only the schema name changes; all tables, indexes, constraints, and data are
-- preserved exactly as-is — no structural modifications are made.
--
-- After this migration, the Java entity layer must be updated to reflect
-- schema = "sage" on all @Table annotations (see handoff for the full list).

ALTER SCHEMA ai RENAME TO sage;
