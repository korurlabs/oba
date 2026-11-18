-- Village Database Schema - Agent Activity Logs
-- V1_2_52__Agent_Activity_Logs.sql
--
-- Creates the sage.agent_activity_logs table to record all agent activity events
-- (AI-driven or user-triggered) within the Sage app. Each row captures the
-- context (app, direction, activity type), the triggering user, an optional
-- link to a domain entity or roadmap item, scheduling metadata, AI use-case
-- attribution, and a free-form JSONB metadata bag for extensibility.

-- =====================================================
-- 1. Create tables
-- =====================================================

CREATE TABLE sage.agent_activity_logs (
    id                   UUID                     NOT NULL DEFAULT gen_random_uuid(),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    organization_id      UUID                     NOT NULL,
    app_context          VARCHAR(30)              NOT NULL,
    direction            VARCHAR(20)              NOT NULL,
    activity_type        VARCHAR(60)              NOT NULL,
    description          TEXT                     NOT NULL,
    status               VARCHAR(20)              NOT NULL,
    triggered_by_user_id UUID,
    entity_type          VARCHAR(100),
    entity_id            UUID,
    roadmap_item_id      UUID,
    scheduled_for        TIMESTAMP WITH TIME ZONE,
    ai_use_case          VARCHAR(50),
    error_message        TEXT,
    metadata             JSONB,
    CONSTRAINT agent_activity_logs_pk
        PRIMARY KEY (id),
    CONSTRAINT agent_activity_logs_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id)
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

CREATE INDEX agent_activity_logs_organization_id_idx
    ON sage.agent_activity_logs (organization_id);

CREATE INDEX agent_activity_logs_app_context_idx
    ON sage.agent_activity_logs (app_context);

CREATE INDEX agent_activity_logs_direction_idx
    ON sage.agent_activity_logs (direction);

CREATE INDEX agent_activity_logs_created_at_idx
    ON sage.agent_activity_logs (created_at DESC);

CREATE INDEX agent_activity_logs_entity_type_entity_id_idx
    ON sage.agent_activity_logs (entity_type, entity_id);

CREATE INDEX agent_activity_logs_roadmap_item_id_idx
    ON sage.agent_activity_logs (roadmap_item_id)
    WHERE roadmap_item_id IS NOT NULL;
