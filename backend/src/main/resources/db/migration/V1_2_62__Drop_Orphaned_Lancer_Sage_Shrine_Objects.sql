-- Village Database Schema - Drop Orphaned Lancer, Sage and Shrine Objects
-- V1_2_62__Drop_Orphaned_Lancer_Sage_Shrine_Objects.sql
--
-- Follow-up to V1_2_61__Drop_Iroh_Schema.sql. A large dead-code cleanup plus the full
-- retirement of the Lancer app removed ~660 Java files from the Village backend, leaving
-- three groups of tables with no surviving JPA entity. This migration drops that
-- footprint. It is IRREVERSIBLE and destroys any rows still present.
--
-- Inventory method (do not trust the app names alone -- the schemas were renamed twice):
--   - Every CREATE TABLE across the whole migration directory was enumerated, then
--     replayed through the rename migrations (V1_2_9 sage->ai, V1_2_51 ai->sage) to get
--     the CURRENT physical names.
--   - Each resulting table was checked against every @Table / @CollectionTable /
--     @JoinTable / @SecondaryTable annotation in src/main/java. The backend currently
--     maps exactly 25 tables; anything outside that set and inside these three schemas is
--     dropped here.
--
-- IMPORTANT -- two similarly named tables, only one of which is dead:
--   - sage.agent_activity_logs  -> LIVE. Entity AgentActivityLog, backs /ai/activity-logs
--     which the sage frontend calls. Created by V1_2_52. NOT touched by this migration.
--   - sage.ai_activity_logs     -> DEAD. Created as sage.sage_activity_logs in V1_1_15 and
--     renamed to ai_activity_logs by V1_2_9. Its entity (ActivityLog) was deleted. Dropped.
--   Because sage.agent_activity_logs survives, the "sage" schema itself is NOT dropped and
--   the dead tables are removed one by one.
--
-- The "shrine" schema also survives (users, organizations, attachments, notifications,
-- outbox/retry events, user_invitations, organization_members are all live), so only the
-- five orphaned shrine tables are dropped individually.

-- =====================================================
-- 1. Drop the lancer schema and everything inside it
--
--    Lancer is fully retired: zero Java entities map to the schema and no table outside
--    it holds a foreign key into it (verified across every migration -- all lancer FKs are
--    lancer-internal: pods->nodes, containers->images/pods, image_names->images,
--    nodes_images->nodes/images, log_stream_match_labels->log_streams,
--    log_stream_pods->log_streams, log_stream_pod_events->log_stream_pods).
--
--    CASCADE is used deliberately: the schema is non-empty, and CASCADE removes its 17
--    tables plus every index and constraint in dependency-safe order without us having to
--    hand-order the internal FK graph. It also removes the 3 enum types that WERE created
--    schema-qualified and therefore live inside the schema:
--      lancer.health_application_health_status_enum
--      lancer.version_version_type_enum
--      lancer.version_discovered_version_status_enum
--    CASCADE cannot reach outside "lancer" because nothing outside depends on it.
--
--    Tables removed (17): application_health_snapshots, containers, discovered_dead_events,
--    discovered_versions, image_names, images, log_stream_match_labels,
--    log_stream_pod_events, log_stream_pods, log_streams, node_metrics_snapshots, nodes,
--    nodes_images, pod_metrics_snapshots, pods, tls_insights, unused_resources.
-- =====================================================

DROP SCHEMA IF EXISTS lancer CASCADE;

-- =====================================================
-- 2. Drop Lancer enum types that live in "public"
--
--    These 8 were created WITHOUT a schema qualifier (default search_path = public), so
--    they survive the schema drop above and must be dropped explicitly. Safe now: every
--    column referencing them went away in section 1.
-- =====================================================

DROP TYPE IF EXISTS lancer_container_status_enum;
DROP TYPE IF EXISTS lancer_image_pull_policy_enum;
DROP TYPE IF EXISTS lancer_kubernetes_workload_type_enum;
DROP TYPE IF EXISTS lancer_log_retention_day_enum;
DROP TYPE IF EXISTS lancer_log_stream_pod_event_type_enum;
DROP TYPE IF EXISTS lancer_log_stream_pod_status_enum;
DROP TYPE IF EXISTS lancer_tls_insight_severity_enum;
DROP TYPE IF EXISTS lancer_tls_insight_type_enum;

-- =====================================================
-- 3. Drop the dead Sage agent-runtime tables (19)
--
--    All are the "ai_*" physical names produced by V1_2_9's table renames; the V1_2_51
--    schema rename did NOT rename the tables back. None has a surviving entity.
--    sage.agent_activity_logs is deliberately absent from this list.
--
--    Ordered children-before-parents so no CASCADE is needed anywhere here.
-- =====================================================

-- 3a. Leaf tables (referenced by nothing)
DROP TABLE IF EXISTS sage.ai_agent_feedbacks;          -- depends on ai_messages
DROP TABLE IF EXISTS sage.ai_agent_tool_executions;    -- depends on ai_agent_sessions, ai_agent_tool_definitions
DROP TABLE IF EXISTS sage.ai_agent_tool_assignments;   -- depends on ai_agent_registrations, ai_agent_tool_definitions
DROP TABLE IF EXISTS sage.ai_agent_memories;           -- depends on ai_agent_registrations, ai_agent_sessions
DROP TABLE IF EXISTS sage.ai_agent_profiles;           -- depends on ai_agent_registrations
DROP TABLE IF EXISTS sage.ai_agent_permissions;        -- depends on ai_agent_registrations
DROP TABLE IF EXISTS sage.ai_agent_schedules;          -- depends on ai_agent_registrations
DROP TABLE IF EXISTS sage.ai_agent_guardrails;         -- depends on ai_agent_registrations
DROP TABLE IF EXISTS sage.ai_agent_instructions;       -- depends on ai_agent_registrations
DROP TABLE IF EXISTS sage.ai_agent_event_subscriptions;-- depends on ai_agent_registrations
DROP TABLE IF EXISTS sage.ai_messages;                 -- depends on ai_conversations

-- 3b. Mid-level parents (their children are gone)
DROP TABLE IF EXISTS sage.ai_agent_sessions;           -- was referenced by tool_executions, memories
DROP TABLE IF EXISTS sage.ai_agent_tool_definitions;   -- was referenced by tool_executions, tool_assignments
DROP TABLE IF EXISTS sage.ai_conversations;            -- was referenced by ai_messages

-- 3c. Root parent
DROP TABLE IF EXISTS sage.ai_agent_registrations;      -- was referenced by 8 tables above

-- 3d. Standalone tables (no sage-internal FKs in either direction)
DROP TABLE IF EXISTS sage.ai_activity_logs;            -- NOT agent_activity_logs (that one is LIVE)
DROP TABLE IF EXISTS sage.ai_agent_events;
DROP TABLE IF EXISTS sage.ai_agent_metrics;
DROP TABLE IF EXISTS sage.ai_agent_prompt_templates;

-- The Sage agent runtime used VARCHAR status columns throughout -- it never created a
-- single PostgreSQL enum type -- so there is nothing to drop for it in this section.

-- =====================================================
-- 4. Drop the orphaned Shrine tables (5)
--
--    The shrine schema itself stays: users, users_roles, organizations,
--    organization_members, user_invitations, attachments, notifications, outbox_events,
--    outbox_dead_events and retry_events / retry_dead_events all still have entities.
--
--    Ordered child-before-parent; no CASCADE needed.
-- =====================================================

DROP TABLE IF EXISTS shrine.subscription_payments;     -- depends on shrine.subscriptions
DROP TABLE IF EXISTS shrine.subscriptions;             -- was referenced by subscription_payments

DROP TABLE IF EXISTS shrine.photos;                    -- storage sub-domain, entity deleted

-- Deleted shrine/review domain (created by V1_1_14). Neither table has any foreign key,
-- in or out, so their order relative to each other does not matter.
DROP TABLE IF EXISTS shrine.acknowledgements;
DROP TABLE IF EXISTS shrine.approvals;

-- =====================================================
-- 5. Drop Shrine enum types exclusive to the tables above (6)
--
--    Safe only now that section 4 removed every column using them.
--
--    The three approval_* types were created SCHEMA-QUALIFIED and so live inside the
--    surviving "shrine" schema -- they must be named with the schema prefix here. The
--    three subscription types were created unqualified and live in "public".
-- =====================================================

DROP TYPE IF EXISTS shrine.approval_decision_enum;
DROP TYPE IF EXISTS shrine.approval_status_enum;
DROP TYPE IF EXISTS shrine.approval_subject_type_enum;

DROP TYPE IF EXISTS shrine_subscription_payment_status_enum;
DROP TYPE IF EXISTS shrine_subscription_plan_enum;
DROP TYPE IF EXISTS shrine_subscription_status_enum;

-- Intentionally NOT dropped: shrine_storage_entity_type_enum. shrine.photos used it, but
-- so does the LIVE shrine.attachments table. It stays, along with its now-unused
-- 'INVOICE' label (see the residual note in V1_2_61).
