-- Village Database Schema - Create Sage Schema
-- V1_1_18__Sage_Add_Slug_To_Agent_Registration.sql
--
-- Complete creation of the Sage agent platform schema. This is the single
-- source of truth for all sage tables. Agent identity is slug-based from
-- the start — no agent_type column exists anywhere. No kutalmis-specific
-- tables are created.
--
-- Tables created:
--   Core:          sage_agent_registrations, sage_agent_sessions,
--                  sage_agent_memories
--   Configuration: sage_agent_profiles, sage_agent_permissions,
--                  sage_agent_schedules, sage_agent_guardrails
--   Tool registry: sage_agent_tool_definitions, sage_agent_tool_assignments,
--                  sage_agent_tool_executions
--   Prompt mgmt:   sage_agent_prompt_templates, sage_agent_instructions
--   Collaboration: sage_agent_events, sage_agent_event_subscriptions
--   Chat:          sage_conversations, sage_messages
--   Observability: sage_agent_metrics, sage_agent_feedbacks
--   Activity:      sage_activity_logs

-- =====================================================
-- 1. Schema
-- =====================================================

CREATE SCHEMA IF NOT EXISTS sage;

-- =====================================================
-- 2. Core agent tables
-- =====================================================

-- AgentRegistration: one record per agent type per organization
CREATE TABLE sage.sage_agent_registrations (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    slug            VARCHAR(100) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    configuration   JSONB,
    organization_id UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_registrations_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_registrations_organization_id_slug_uk UNIQUE (organization_id, slug),
    CONSTRAINT sage_agent_registrations_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- AgentSession: one execution run per agent
CREATE TABLE sage.sage_agent_sessions (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id   UUID NOT NULL,
    organization_id         UUID NOT NULL,
    session_status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at            TIMESTAMP WITH TIME ZONE,
    trigger_type            VARCHAR(20) NOT NULL,
    summary                 VARCHAR(500),
    tokens_used             INTEGER,
    provider_used           VARCHAR(50),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_sessions_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_sessions_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT agent_sessions_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- AgentMemory: persistent knowledge entries per agent
CREATE TABLE sage.sage_agent_memories (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id   UUID NOT NULL,
    organization_id         UUID NOT NULL,
    memory_key              VARCHAR(255) NOT NULL,
    memory_value            TEXT NOT NULL,
    confidence              NUMERIC NOT NULL,
    source_session_id       UUID,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_memories_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_memories_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT sage_agent_memories_source_session_id_fk FOREIGN KEY (source_session_id)
        REFERENCES sage.sage_agent_sessions (id) ON DELETE SET NULL,
    CONSTRAINT agent_memories_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Agent configuration portal
-- =====================================================

-- AgentProfile: personality and behavior settings
CREATE TABLE sage.sage_agent_profiles (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id   UUID NOT NULL,
    organization_id         UUID NOT NULL,
    language                VARCHAR(5) NOT NULL DEFAULT 'en',
    tone                    VARCHAR(20) NOT NULL DEFAULT 'PROFESSIONAL',
    verbosity               VARCHAR(20) NOT NULL DEFAULT 'BALANCED',
    autonomy_level          VARCHAR(20) NOT NULL DEFAULT 'SUPERVISED',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_profiles_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_profiles_agent_registration_id_uk UNIQUE (agent_registration_id),
    CONSTRAINT sage_agent_profiles_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT agent_profiles_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- AgentPermission: domain access control per agent
CREATE TABLE sage.sage_agent_permissions (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id   UUID NOT NULL,
    organization_id         UUID NOT NULL,
    target_domain           VARCHAR(50) NOT NULL,
    can_read                BOOLEAN NOT NULL DEFAULT false,
    can_write               BOOLEAN NOT NULL DEFAULT false,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_permissions_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_permissions_agent_registration_id_target_domain_uk UNIQUE (agent_registration_id, target_domain),
    CONSTRAINT sage_agent_permissions_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT agent_permissions_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- AgentSchedule: user-configurable recurring tasks
CREATE TABLE sage.sage_agent_schedules (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id   UUID NOT NULL,
    organization_id         UUID NOT NULL,
    task_name               VARCHAR(100) NOT NULL,
    cron_expression         VARCHAR(100) NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    last_run_at             TIMESTAMP WITH TIME ZONE,
    next_run_at             TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_schedules_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_schedules_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT agent_schedules_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- AgentGuardrail: safety limits per agent
CREATE TABLE sage.sage_agent_guardrails (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id   UUID NOT NULL,
    organization_id         UUID NOT NULL,
    guardrail_type          VARCHAR(50) NOT NULL,
    threshold_value         NUMERIC NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    action_on_breach        VARCHAR(30) NOT NULL DEFAULT 'NOTIFY_ADMIN',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_guardrails_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_guardrails_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT agent_guardrails_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- =====================================================
-- 4. Tool registry
-- =====================================================

-- AgentToolDefinition: capability registry — shared across all orgs
CREATE TABLE sage.sage_agent_tool_definitions (
    id                          UUID NOT NULL DEFAULT gen_random_uuid(),
    name                        VARCHAR(100) NOT NULL,
    display_name                VARCHAR(200) NOT NULL,
    description                 TEXT NOT NULL,
    target_domain               VARCHAR(50) NOT NULL,
    target_service              VARCHAR(200) NOT NULL,
    target_method               VARCHAR(200) NOT NULL,
    input_schema                JSONB NOT NULL,
    output_schema               JSONB,
    requires_approval           BOOLEAN NOT NULL DEFAULT false,
    risk_level                  VARCHAR(20) NOT NULL DEFAULT 'LOW',
    is_active                   BOOLEAN NOT NULL DEFAULT true,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_tool_definitions_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_tool_definitions_name_uk UNIQUE (name)
);

-- AgentToolAssignment: which agents have which tools — per org
CREATE TABLE sage.sage_agent_tool_assignments (
    id                          UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id       UUID NOT NULL,
    tool_definition_id          UUID NOT NULL,
    organization_id             UUID NOT NULL,
    is_enabled                  BOOLEAN NOT NULL DEFAULT true,
    override_requires_approval  BOOLEAN,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_tool_assignments_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_tool_assignments_agent_registration_id_tool_definition_id_uk
        UNIQUE (agent_registration_id, tool_definition_id),
    CONSTRAINT sage_agent_tool_assignments_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT sage_agent_tool_assignments_tool_definition_id_fk FOREIGN KEY (tool_definition_id)
        REFERENCES sage.sage_agent_tool_definitions (id) ON DELETE CASCADE,
    CONSTRAINT agent_tool_assignments_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- AgentToolExecution: audit trail of every tool action
CREATE TABLE sage.sage_agent_tool_executions (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_session_id    UUID NOT NULL,
    tool_definition_id  UUID NOT NULL,
    organization_id     UUID NOT NULL,
    input_payload       JSONB,
    output_payload      JSONB,
    execution_status    VARCHAR(30) NOT NULL,
    duration_ms         BIGINT,
    error_message       TEXT,
    approved_by         UUID,
    executed_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_tool_executions_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_tool_executions_agent_session_id_fk FOREIGN KEY (agent_session_id)
        REFERENCES sage.sage_agent_sessions (id) ON DELETE CASCADE,
    CONSTRAINT sage_agent_tool_executions_tool_definition_id_fk FOREIGN KEY (tool_definition_id)
        REFERENCES sage.sage_agent_tool_definitions (id),
    CONSTRAINT agent_tool_executions_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT agent_tool_executions_approved_by_fk FOREIGN KEY (approved_by)
        REFERENCES shrine.users (id)
);

-- =====================================================
-- 5. Prompt management
-- =====================================================

-- AgentPromptTemplate: versioned, deployable prompts identified by slug
CREATE TABLE sage.sage_agent_prompt_templates (
    id               UUID NOT NULL DEFAULT gen_random_uuid(),
    slug             VARCHAR(100) NOT NULL,
    prompt_type      VARCHAR(30) NOT NULL,
    template_content TEXT NOT NULL,
    version          INTEGER NOT NULL,
    is_active        BOOLEAN NOT NULL DEFAULT false,
    description      VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_prompt_templates_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_prompt_templates_slug_prompt_type_version_uk UNIQUE (slug, prompt_type, version)
);

-- AgentInstruction: user-defined instructions per agent
CREATE TABLE sage.sage_agent_instructions (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id   UUID NOT NULL,
    organization_id         UUID NOT NULL,
    instruction_text        TEXT NOT NULL,
    priority                INTEGER NOT NULL DEFAULT 0,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_instructions_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_instructions_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT agent_instructions_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- =====================================================
-- 6. Agent collaboration (events)
-- =====================================================

-- AgentEvent: inter-agent communication, identified by source slug
CREATE TABLE sage.sage_agent_events (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    source_agent_slug   VARCHAR(100) NOT NULL,
    organization_id     UUID NOT NULL,
    event_type          VARCHAR(50) NOT NULL,
    summary             VARCHAR(500) NOT NULL,
    payload             JSONB,
    severity            VARCHAR(20) NOT NULL DEFAULT 'INFO',
    is_consumed         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_events_pk PRIMARY KEY (id),
    CONSTRAINT agent_events_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- AgentEventSubscription: who listens to what
CREATE TABLE sage.sage_agent_event_subscriptions (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_registration_id   UUID NOT NULL,
    organization_id         UUID NOT NULL,
    event_type              VARCHAR(50) NOT NULL,
    source_agent_slug       VARCHAR(100),
    is_active               BOOLEAN NOT NULL DEFAULT true,
    action_on_receive       VARCHAR(30) NOT NULL DEFAULT 'NOTIFY_USER',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_event_subscriptions_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_event_subscriptions_agent_registration_id_fk FOREIGN KEY (agent_registration_id)
        REFERENCES sage.sage_agent_registrations (id) ON DELETE CASCADE,
    CONSTRAINT agent_event_subscriptions_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- =====================================================
-- 7. Chat tables
-- =====================================================

-- Conversation: chat session container — routed by agent slug
CREATE TABLE sage.sage_conversations (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    title           VARCHAR(255) NOT NULL,
    archived        BOOLEAN NOT NULL DEFAULT false,
    message_count   INTEGER NOT NULL DEFAULT 0,
    slug            VARCHAR(100) NOT NULL,
    owner_id        UUID NOT NULL,
    organization_id UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_conversations_pk PRIMARY KEY (id),
    CONSTRAINT sage_conversations_owner_id_fk FOREIGN KEY (owner_id)
        REFERENCES shrine.users (id),
    CONSTRAINT sage_conversations_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- Message: individual chat messages
CREATE TABLE sage.sage_messages (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    tokens_used     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_messages_pk PRIMARY KEY (id),
    CONSTRAINT sage_messages_conversation_id_fk FOREIGN KEY (conversation_id)
        REFERENCES sage.sage_conversations (id) ON DELETE CASCADE
);

-- =====================================================
-- 8. Observability and feedback
-- =====================================================

-- AgentMetric: time-series performance data identified by slug
CREATE TABLE sage.sage_agent_metrics (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    slug            VARCHAR(100) NOT NULL,
    organization_id UUID NOT NULL,
    metric_name     VARCHAR(50) NOT NULL,
    metric_value    NUMERIC NOT NULL,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    period_type     VARCHAR(10) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_metrics_pk PRIMARY KEY (id),
    CONSTRAINT agent_metrics_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- AgentFeedback: user feedback on agent messages
CREATE TABLE sage.sage_agent_feedbacks (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    message_id      UUID NOT NULL,
    organization_id UUID NOT NULL,
    user_id         UUID NOT NULL,
    rating          VARCHAR(10) NOT NULL,
    comment         TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_agent_feedbacks_pk PRIMARY KEY (id),
    CONSTRAINT sage_agent_feedbacks_message_id_user_id_uk UNIQUE (message_id, user_id),
    CONSTRAINT sage_agent_feedbacks_message_id_fk FOREIGN KEY (message_id)
        REFERENCES sage.sage_messages (id) ON DELETE CASCADE,
    CONSTRAINT agent_feedbacks_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT agent_feedbacks_user_id_fk FOREIGN KEY (user_id)
        REFERENCES shrine.users (id)
);

-- =====================================================
-- 9. Activity logs
-- =====================================================

-- ActivityLog: audit trail of agent actions identified by slug
CREATE TABLE sage.sage_activity_logs (
    id               UUID NOT NULL DEFAULT gen_random_uuid(),
    action           VARCHAR(100) NOT NULL,
    description      TEXT,
    target_domain    VARCHAR(100),
    target_entity_id UUID,
    slug             VARCHAR(100) NOT NULL,
    organization_id  UUID NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sage_activity_logs_pk PRIMARY KEY (id),
    CONSTRAINT sage_activity_logs_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations (id) ON DELETE CASCADE
);

-- =====================================================
-- 10. Indexes
-- =====================================================

-- Agent registrations
CREATE INDEX sage_agent_registrations_slug_idx ON sage.sage_agent_registrations (slug);
CREATE INDEX sage_agent_registrations_organization_id_idx ON sage.sage_agent_registrations (organization_id);

-- Agent sessions
CREATE INDEX sage_agent_sessions_agent_registration_id_idx ON sage.sage_agent_sessions (agent_registration_id);
CREATE INDEX sage_agent_sessions_organization_id_idx ON sage.sage_agent_sessions (organization_id);

-- Agent memories
CREATE INDEX sage_agent_memories_agent_registration_id_idx ON sage.sage_agent_memories (agent_registration_id);

-- Agent profiles
CREATE INDEX sage_agent_profiles_agent_registration_id_idx ON sage.sage_agent_profiles (agent_registration_id);

-- Agent permissions
CREATE INDEX sage_agent_permissions_agent_registration_id_idx ON sage.sage_agent_permissions (agent_registration_id);

-- Agent schedules
CREATE INDEX sage_agent_schedules_due_idx ON sage.sage_agent_schedules (is_active, next_run_at);
CREATE INDEX sage_agent_schedules_agent_registration_id_idx ON sage.sage_agent_schedules (agent_registration_id);

-- Agent guardrails
CREATE INDEX sage_agent_guardrails_agent_registration_id_idx ON sage.sage_agent_guardrails (agent_registration_id);

-- Tool definitions
CREATE INDEX sage_agent_tool_definitions_target_domain_idx ON sage.sage_agent_tool_definitions (target_domain);

-- Tool assignments
CREATE INDEX sage_agent_tool_assignments_agent_registration_id_idx ON sage.sage_agent_tool_assignments (agent_registration_id);
CREATE INDEX sage_agent_tool_assignments_tool_definition_id_idx ON sage.sage_agent_tool_assignments (tool_definition_id);

-- Tool executions
CREATE INDEX sage_agent_tool_executions_agent_session_id_idx ON sage.sage_agent_tool_executions (agent_session_id);
CREATE INDEX sage_agent_tool_executions_organization_id_execution_status_idx ON sage.sage_agent_tool_executions (organization_id, execution_status);

-- Prompt templates
CREATE INDEX sage_agent_prompt_templates_slug_prompt_type_idx ON sage.sage_agent_prompt_templates (slug, prompt_type);

-- Instructions
CREATE INDEX sage_agent_instructions_agent_registration_id_idx ON sage.sage_agent_instructions (agent_registration_id);

-- Agent events
CREATE INDEX sage_agent_events_organization_id_is_consumed_idx ON sage.sage_agent_events (organization_id, is_consumed);
CREATE INDEX sage_agent_events_source_agent_slug_idx ON sage.sage_agent_events (source_agent_slug);

-- Event subscriptions
CREATE INDEX sage_agent_event_subscriptions_agent_registration_id_idx ON sage.sage_agent_event_subscriptions (agent_registration_id);

-- Conversations
CREATE INDEX sage_conversations_owner_id_idx ON sage.sage_conversations (owner_id);
CREATE INDEX sage_conversations_organization_id_idx ON sage.sage_conversations (organization_id);
CREATE INDEX sage_conversations_slug_idx ON sage.sage_conversations (slug);

-- Messages
CREATE INDEX sage_messages_conversation_id_idx ON sage.sage_messages (conversation_id);
CREATE INDEX sage_messages_created_at_idx ON sage.sage_messages (created_at);

-- Metrics
CREATE INDEX sage_agent_metrics_lookup_idx ON sage.sage_agent_metrics (slug, organization_id, metric_name, recorded_at);

-- Feedbacks
CREATE INDEX sage_agent_feedbacks_message_id_idx ON sage.sage_agent_feedbacks (message_id);

-- Activity logs
CREATE INDEX sage_activity_logs_organization_id_idx ON sage.sage_activity_logs (organization_id);
CREATE INDEX sage_activity_logs_created_at_idx ON sage.sage_activity_logs (created_at);
CREATE INDEX sage_activity_logs_slug_idx ON sage.sage_activity_logs (slug);
