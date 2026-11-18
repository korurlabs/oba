-- Village Database Schema - Rename Sage Schema To AI
-- V1_2_9__Rename_Sage_Schema_To_AI.sql
--
-- Renames the `sage` schema and all its objects to `ai` as part of the
-- apps.sage → apps.ai hostname/branding migration. The schema itself is
-- renamed first; all tables, indexes, and constraints retain their
-- structural definitions — only identifiers that embed "sage" are updated
-- for consistency with the new `ai_*` naming convention.
--
-- No PostgreSQL native ENUM types exist in this schema — all status/type
-- columns use VARCHAR — so no ALTER TYPE statements are required.

-- =====================================================
-- 1. Rename schema
-- =====================================================

ALTER SCHEMA sage RENAME TO ai;

-- =====================================================
-- 2. Rename tables (schema is now "ai", tables still named "sage_*")
-- =====================================================

-- Core agent tables
ALTER TABLE ai.sage_agent_registrations          RENAME TO ai_agent_registrations;
ALTER TABLE ai.sage_agent_sessions               RENAME TO ai_agent_sessions;
ALTER TABLE ai.sage_agent_memories               RENAME TO ai_agent_memories;

-- Configuration tables
ALTER TABLE ai.sage_agent_profiles               RENAME TO ai_agent_profiles;
ALTER TABLE ai.sage_agent_permissions            RENAME TO ai_agent_permissions;
ALTER TABLE ai.sage_agent_schedules              RENAME TO ai_agent_schedules;
ALTER TABLE ai.sage_agent_guardrails             RENAME TO ai_agent_guardrails;

-- Tool registry tables
ALTER TABLE ai.sage_agent_tool_definitions       RENAME TO ai_agent_tool_definitions;
ALTER TABLE ai.sage_agent_tool_assignments       RENAME TO ai_agent_tool_assignments;
ALTER TABLE ai.sage_agent_tool_executions        RENAME TO ai_agent_tool_executions;

-- Prompt management tables
ALTER TABLE ai.sage_agent_prompt_templates       RENAME TO ai_agent_prompt_templates;
ALTER TABLE ai.sage_agent_instructions           RENAME TO ai_agent_instructions;

-- Collaboration tables
ALTER TABLE ai.sage_agent_events                 RENAME TO ai_agent_events;
ALTER TABLE ai.sage_agent_event_subscriptions    RENAME TO ai_agent_event_subscriptions;

-- Chat tables
ALTER TABLE ai.sage_conversations                RENAME TO ai_conversations;
ALTER TABLE ai.sage_messages                     RENAME TO ai_messages;

-- Observability tables
ALTER TABLE ai.sage_agent_metrics                RENAME TO ai_agent_metrics;
ALTER TABLE ai.sage_agent_feedbacks              RENAME TO ai_agent_feedbacks;

-- Activity table
ALTER TABLE ai.sage_activity_logs                RENAME TO ai_activity_logs;

-- =====================================================
-- 3. Rename indexes
-- =====================================================

-- Agent registrations
ALTER INDEX ai.sage_agent_registrations_slug_idx              RENAME TO ai_agent_registrations_slug_idx;
ALTER INDEX ai.sage_agent_registrations_organization_id_idx   RENAME TO ai_agent_registrations_organization_id_idx;

-- Agent sessions
ALTER INDEX ai.sage_agent_sessions_agent_registration_id_idx  RENAME TO ai_agent_sessions_agent_registration_id_idx;
ALTER INDEX ai.sage_agent_sessions_organization_id_idx        RENAME TO ai_agent_sessions_organization_id_idx;

-- Agent memories
ALTER INDEX ai.sage_agent_memories_agent_registration_id_idx  RENAME TO ai_agent_memories_agent_registration_id_idx;

-- Agent profiles
ALTER INDEX ai.sage_agent_profiles_agent_registration_id_idx  RENAME TO ai_agent_profiles_agent_registration_id_idx;

-- Agent permissions
ALTER INDEX ai.sage_agent_permissions_agent_registration_id_idx RENAME TO ai_agent_permissions_agent_registration_id_idx;

-- Agent schedules
ALTER INDEX ai.sage_agent_schedules_due_idx                    RENAME TO ai_agent_schedules_due_idx;
ALTER INDEX ai.sage_agent_schedules_agent_registration_id_idx  RENAME TO ai_agent_schedules_agent_registration_id_idx;

-- Agent guardrails
ALTER INDEX ai.sage_agent_guardrails_agent_registration_id_idx RENAME TO ai_agent_guardrails_agent_registration_id_idx;

-- Tool definitions
ALTER INDEX ai.sage_agent_tool_definitions_target_domain_idx   RENAME TO ai_agent_tool_definitions_target_domain_idx;

-- Tool assignments
ALTER INDEX ai.sage_agent_tool_assignments_agent_registration_id_idx RENAME TO ai_agent_tool_assignments_agent_registration_id_idx;
ALTER INDEX ai.sage_agent_tool_assignments_tool_definition_id_idx    RENAME TO ai_agent_tool_assignments_tool_definition_id_idx;

-- Tool executions
ALTER INDEX ai.sage_agent_tool_executions_agent_session_id_idx                    RENAME TO ai_agent_tool_executions_agent_session_id_idx;
ALTER INDEX ai.sage_agent_tool_executions_organization_id_execution_status_idx    RENAME TO ai_agent_tool_executions_organization_id_execution_status_idx;

-- Prompt templates
ALTER INDEX ai.sage_agent_prompt_templates_slug_prompt_type_idx RENAME TO ai_agent_prompt_templates_slug_prompt_type_idx;

-- Instructions
ALTER INDEX ai.sage_agent_instructions_agent_registration_id_idx RENAME TO ai_agent_instructions_agent_registration_id_idx;

-- Agent events
ALTER INDEX ai.sage_agent_events_organization_id_is_consumed_idx RENAME TO ai_agent_events_organization_id_is_consumed_idx;
ALTER INDEX ai.sage_agent_events_source_agent_slug_idx           RENAME TO ai_agent_events_source_agent_slug_idx;

-- Event subscriptions
ALTER INDEX ai.sage_agent_event_subscriptions_agent_registration_id_idx RENAME TO ai_agent_event_subscriptions_agent_registration_id_idx;

-- Conversations
ALTER INDEX ai.sage_conversations_owner_id_idx        RENAME TO ai_conversations_owner_id_idx;
ALTER INDEX ai.sage_conversations_organization_id_idx RENAME TO ai_conversations_organization_id_idx;
ALTER INDEX ai.sage_conversations_slug_idx            RENAME TO ai_conversations_slug_idx;

-- Messages
ALTER INDEX ai.sage_messages_conversation_id_idx RENAME TO ai_messages_conversation_id_idx;
ALTER INDEX ai.sage_messages_created_at_idx      RENAME TO ai_messages_created_at_idx;

-- Metrics
ALTER INDEX ai.sage_agent_metrics_lookup_idx RENAME TO ai_agent_metrics_lookup_idx;

-- Feedbacks
ALTER INDEX ai.sage_agent_feedbacks_message_id_idx RENAME TO ai_agent_feedbacks_message_id_idx;

-- Activity logs
ALTER INDEX ai.sage_activity_logs_organization_id_idx RENAME TO ai_activity_logs_organization_id_idx;
ALTER INDEX ai.sage_activity_logs_created_at_idx      RENAME TO ai_activity_logs_created_at_idx;
ALTER INDEX ai.sage_activity_logs_slug_idx            RENAME TO ai_activity_logs_slug_idx;

-- =====================================================
-- 4. Rename primary key constraints
-- =====================================================

ALTER TABLE ai.ai_agent_registrations       RENAME CONSTRAINT sage_agent_registrations_pk       TO ai_agent_registrations_pk;
ALTER TABLE ai.ai_agent_sessions            RENAME CONSTRAINT sage_agent_sessions_pk             TO ai_agent_sessions_pk;
ALTER TABLE ai.ai_agent_memories            RENAME CONSTRAINT sage_agent_memories_pk             TO ai_agent_memories_pk;
ALTER TABLE ai.ai_agent_profiles            RENAME CONSTRAINT sage_agent_profiles_pk             TO ai_agent_profiles_pk;
ALTER TABLE ai.ai_agent_permissions         RENAME CONSTRAINT sage_agent_permissions_pk          TO ai_agent_permissions_pk;
ALTER TABLE ai.ai_agent_schedules           RENAME CONSTRAINT sage_agent_schedules_pk            TO ai_agent_schedules_pk;
ALTER TABLE ai.ai_agent_guardrails          RENAME CONSTRAINT sage_agent_guardrails_pk           TO ai_agent_guardrails_pk;
ALTER TABLE ai.ai_agent_tool_definitions    RENAME CONSTRAINT sage_agent_tool_definitions_pk     TO ai_agent_tool_definitions_pk;
ALTER TABLE ai.ai_agent_tool_assignments    RENAME CONSTRAINT sage_agent_tool_assignments_pk     TO ai_agent_tool_assignments_pk;
ALTER TABLE ai.ai_agent_tool_executions     RENAME CONSTRAINT sage_agent_tool_executions_pk      TO ai_agent_tool_executions_pk;
ALTER TABLE ai.ai_agent_prompt_templates    RENAME CONSTRAINT sage_agent_prompt_templates_pk     TO ai_agent_prompt_templates_pk;
ALTER TABLE ai.ai_agent_instructions        RENAME CONSTRAINT sage_agent_instructions_pk         TO ai_agent_instructions_pk;
ALTER TABLE ai.ai_agent_events              RENAME CONSTRAINT sage_agent_events_pk               TO ai_agent_events_pk;
ALTER TABLE ai.ai_agent_event_subscriptions RENAME CONSTRAINT sage_agent_event_subscriptions_pk  TO ai_agent_event_subscriptions_pk;
ALTER TABLE ai.ai_conversations             RENAME CONSTRAINT sage_conversations_pk              TO ai_conversations_pk;
ALTER TABLE ai.ai_messages                  RENAME CONSTRAINT sage_messages_pk                   TO ai_messages_pk;
ALTER TABLE ai.ai_agent_metrics             RENAME CONSTRAINT sage_agent_metrics_pk              TO ai_agent_metrics_pk;
ALTER TABLE ai.ai_agent_feedbacks           RENAME CONSTRAINT sage_agent_feedbacks_pk            TO ai_agent_feedbacks_pk;
ALTER TABLE ai.ai_activity_logs             RENAME CONSTRAINT sage_activity_logs_pk              TO ai_activity_logs_pk;

-- =====================================================
-- 5. Rename unique constraints
-- =====================================================

ALTER TABLE ai.ai_agent_registrations
    RENAME CONSTRAINT sage_agent_registrations_organization_id_slug_uk
    TO ai_agent_registrations_organization_id_slug_uk;

ALTER TABLE ai.ai_agent_profiles
    RENAME CONSTRAINT sage_agent_profiles_agent_registration_id_uk
    TO ai_agent_profiles_agent_registration_id_uk;

ALTER TABLE ai.ai_agent_permissions
    RENAME CONSTRAINT sage_agent_permissions_agent_registration_id_target_domain_uk
    TO ai_agent_permissions_agent_registration_id_target_domain_uk;

ALTER TABLE ai.ai_agent_tool_definitions
    RENAME CONSTRAINT sage_agent_tool_definitions_name_uk
    TO ai_agent_tool_definitions_name_uk;

ALTER TABLE ai.ai_agent_tool_assignments
    RENAME CONSTRAINT sage_agent_tool_assignments_agent_registration_id_tool_definition_id_uk
    TO ai_agent_tool_assignments_agent_registration_id_tool_definition_id_uk;

ALTER TABLE ai.ai_agent_prompt_templates
    RENAME CONSTRAINT sage_agent_prompt_templates_slug_prompt_type_version_uk
    TO ai_agent_prompt_templates_slug_prompt_type_version_uk;

ALTER TABLE ai.ai_agent_feedbacks
    RENAME CONSTRAINT sage_agent_feedbacks_message_id_user_id_uk
    TO ai_agent_feedbacks_message_id_user_id_uk;

-- =====================================================
-- 6. Rename foreign key constraints that embed "sage"
-- =====================================================

-- ai_agent_registrations
ALTER TABLE ai.ai_agent_registrations
    RENAME CONSTRAINT sage_agent_registrations_organization_id_fk
    TO ai_agent_registrations_organization_id_fk;

-- ai_agent_sessions
ALTER TABLE ai.ai_agent_sessions
    RENAME CONSTRAINT sage_agent_sessions_agent_registration_id_fk
    TO ai_agent_sessions_agent_registration_id_fk;

-- ai_agent_memories
ALTER TABLE ai.ai_agent_memories
    RENAME CONSTRAINT sage_agent_memories_agent_registration_id_fk
    TO ai_agent_memories_agent_registration_id_fk;
ALTER TABLE ai.ai_agent_memories
    RENAME CONSTRAINT sage_agent_memories_source_session_id_fk
    TO ai_agent_memories_source_session_id_fk;

-- ai_agent_profiles
ALTER TABLE ai.ai_agent_profiles
    RENAME CONSTRAINT sage_agent_profiles_agent_registration_id_fk
    TO ai_agent_profiles_agent_registration_id_fk;

-- ai_agent_permissions
ALTER TABLE ai.ai_agent_permissions
    RENAME CONSTRAINT sage_agent_permissions_agent_registration_id_fk
    TO ai_agent_permissions_agent_registration_id_fk;

-- ai_agent_schedules
ALTER TABLE ai.ai_agent_schedules
    RENAME CONSTRAINT sage_agent_schedules_agent_registration_id_fk
    TO ai_agent_schedules_agent_registration_id_fk;

-- ai_agent_guardrails
ALTER TABLE ai.ai_agent_guardrails
    RENAME CONSTRAINT sage_agent_guardrails_agent_registration_id_fk
    TO ai_agent_guardrails_agent_registration_id_fk;

-- ai_agent_tool_assignments (only sage-prefixed FK)
ALTER TABLE ai.ai_agent_tool_assignments
    RENAME CONSTRAINT sage_agent_tool_assignments_agent_registration_id_fk
    TO ai_agent_tool_assignments_agent_registration_id_fk;
ALTER TABLE ai.ai_agent_tool_assignments
    RENAME CONSTRAINT sage_agent_tool_assignments_tool_definition_id_fk
    TO ai_agent_tool_assignments_tool_definition_id_fk;

-- ai_agent_tool_executions
ALTER TABLE ai.ai_agent_tool_executions
    RENAME CONSTRAINT sage_agent_tool_executions_agent_session_id_fk
    TO ai_agent_tool_executions_agent_session_id_fk;
ALTER TABLE ai.ai_agent_tool_executions
    RENAME CONSTRAINT sage_agent_tool_executions_tool_definition_id_fk
    TO ai_agent_tool_executions_tool_definition_id_fk;

-- ai_agent_instructions
ALTER TABLE ai.ai_agent_instructions
    RENAME CONSTRAINT sage_agent_instructions_agent_registration_id_fk
    TO ai_agent_instructions_agent_registration_id_fk;

-- ai_agent_event_subscriptions
ALTER TABLE ai.ai_agent_event_subscriptions
    RENAME CONSTRAINT sage_agent_event_subscriptions_agent_registration_id_fk
    TO ai_agent_event_subscriptions_agent_registration_id_fk;

-- ai_conversations
ALTER TABLE ai.ai_conversations
    RENAME CONSTRAINT sage_conversations_owner_id_fk
    TO ai_conversations_owner_id_fk;
ALTER TABLE ai.ai_conversations
    RENAME CONSTRAINT sage_conversations_organization_id_fk
    TO ai_conversations_organization_id_fk;

-- ai_messages
ALTER TABLE ai.ai_messages
    RENAME CONSTRAINT sage_messages_conversation_id_fk
    TO ai_messages_conversation_id_fk;

-- ai_agent_feedbacks
ALTER TABLE ai.ai_agent_feedbacks
    RENAME CONSTRAINT sage_agent_feedbacks_message_id_fk
    TO ai_agent_feedbacks_message_id_fk;

-- ai_activity_logs
ALTER TABLE ai.ai_activity_logs
    RENAME CONSTRAINT sage_activity_logs_organization_id_fk
    TO ai_activity_logs_organization_id_fk;
