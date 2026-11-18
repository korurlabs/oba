-- Village Database Schema - Drop Sage Schema
-- V1_2_63__Drop_Sage_Schema.sql
--
-- Follow-up to V1_2_62__Drop_Orphaned_Lancer_Sage_Shrine_Objects.sql. That migration kept
-- sage.agent_activity_logs and the "sage" schema on purpose, because the write side of the
-- activity log was still live. The write side is now gone as well: the package
-- com.sensey.village.apps.sage was deleted, so no entity maps the table any more. This
-- migration drops the table and then the empty schema. It is IRREVERSIBLE and destroys any
-- activity log rows still present.
--
-- Inventory:
--   - sage.agent_activity_logs was created by V1_2_52 as sage.agent_activity_logs. The schema
--     was renamed sage -> ai (V1_2_9) and back ai -> sage (V1_2_51), so "sage" is the current
--     schema name. V1_2_52 was written after V1_2_51 and created the table directly in "sage".
--   - Its only foreign key is agent_activity_logs_organization_id_fk to shrine.organizations
--     (id). It points OUT of the table, so dropping the table needs no change to shrine, and
--     no other table references agent_activity_logs.
--   - The six indexes V1_2_52 created are owned by the table and disappear with it:
--     agent_activity_logs_organization_id_idx, agent_activity_logs_app_context_idx,
--     agent_activity_logs_direction_idx, agent_activity_logs_created_at_idx,
--     agent_activity_logs_entity_type_entity_id_idx and agent_activity_logs_roadmap_item_id_idx.
--   - V1_2_52 created no PostgreSQL enum type: app_context, direction, activity_type, status
--     and ai_use_case are all VARCHAR columns. So there is no enum type to drop.
--   - Every other table in the schema was dropped by V1_2_62 and the Sage runtime never
--     created an enum type, so the table below is the last object in "sage".
--
-- Cascade rationale:
--   The table is dropped explicitly, without CASCADE, and the schema is then dropped WITHOUT
--   CASCADE. If anything unexpected were still inside "sage", the DROP SCHEMA fails and the
--   migration rolls back instead of silently deleting an object nobody inventoried.

-- =====================================================
-- 1. Drop the Sage activity log table (its indexes and foreign key go with it)
-- =====================================================

DROP TABLE IF EXISTS sage.agent_activity_logs;

-- =====================================================
-- 2. Drop the now-empty sage schema
-- =====================================================

DROP SCHEMA IF EXISTS sage;
