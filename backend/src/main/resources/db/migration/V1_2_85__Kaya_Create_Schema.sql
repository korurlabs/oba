-- Oba Database Schema - Kaya: Create Schema
-- V1_2_85__Kaya_Create_Schema.sql
--
-- Adds the Kaya app to the platform database: one schema, kaya, holding every
-- Kaya table in the final shape of the former stand-alone Kaya migrations.
-- Kaya keeps no users or organizations of its own. Every user reference points
-- at shrine.users(id) and every organization reference at shrine.organizations(id),
-- both created by earlier versions of this set. Kaya's own tables for users,
-- organizations, organization members and organization email domains do not exist
-- here, nor their audit of membership and domain changes, which the platform owns;
-- the audit and git-email tables that hang off a user stay in kaya.
--
-- Enum types are created in the public schema with their Kaya names, as the
-- other apps of this set do. A child row of an organization is removed with the
-- organization (ON DELETE CASCADE); a reference to the user who acted is cleared
-- (ON DELETE SET NULL) where the row outlives the actor, and restricts the delete
-- of the user where the row cannot exist without an owner.

-- =====================================================
-- 1. Create schema
-- =====================================================

CREATE SCHEMA IF NOT EXISTS kaya;

-- =====================================================
-- 2. Create enums
-- =====================================================

CREATE TYPE account_account_provider_enum AS ENUM ('CLAUDE', 'CHATGPT', 'GEMINI', 'GROK', 'DEEPSEEK');
CREATE TYPE account_account_user_source_enum AS ENUM ('RECORDER', 'MANUAL');
CREATE TYPE agent_agent_status_enum AS ENUM ('ACTIVE', 'RETIRED');
CREATE TYPE agentsfile_agents_file_placement_enum AS ENUM ('ROOT', 'AGENTS_DIR', 'MOUNT_ROOT');
CREATE TYPE guardrail_guardrail_category_enum AS ENUM ('SECURITY', 'LANGUAGE', 'QUALITY', 'DATA', 'OTHER');
CREATE TYPE identity_user_audit_entry_action_enum AS ENUM ('CREATED', 'RENAMED', 'USER_LINKED', 'EMAIL_ADDED', 'EMAIL_REMOVED', 'ACCOUNT_ADDED', 'ACCOUNT_REMOVED', 'AGENT_CREATED', 'AGENT_RETIRED', 'TOKEN_ISSUED', 'TOKEN_ROTATED', 'TOKEN_REVOKED', 'ALL_REVOKED', 'MERGED_INTO', 'MERGED_FROM');
CREATE TYPE identity_user_audit_entry_actor_kind_enum AS ENUM ('USER', 'SYSTEM', 'RECORDER');
CREATE TYPE identity_user_git_email_source_enum AS ENUM ('SIGN_IN', 'RECORDER', 'MANUAL');
CREATE TYPE plan_plan_feedback_decision_enum AS ENUM ('APPROVE', 'REQUEST_CHANGES', 'COMMENT');
CREATE TYPE plan_plan_kind_enum AS ENUM ('SPEC', 'SESSION');
CREATE TYPE plan_plan_review_state_enum AS ENUM ('OPEN', 'APPROVED', 'CHANGES_REQUESTED');
CREATE TYPE plan_plan_version_source_enum AS ENUM ('FILE', 'PLAN_MODE', 'MESSAGE');
CREATE TYPE repository_repository_status_enum AS ENUM ('ACTIVE', 'ARCHIVED');
CREATE TYPE review_review_decision_enum AS ENUM ('APPROVE', 'REJECT', 'COMMENT');
CREATE TYPE seed_seed_run_status_enum AS ENUM ('PASSED', 'FAILED');
CREATE TYPE session_session_delivered_by_enum AS ENUM ('LAUNCHER', 'HOOK');
CREATE TYPE session_session_link_kind_enum AS ENUM ('PULL_REQUEST', 'KAIZEN_TICKET');
CREATE TYPE session_session_link_source_enum AS ENUM ('RECORDER', 'MANUAL');
CREATE TYPE session_session_release_state_enum AS ENUM ('FRESH', 'CACHED', 'STALE', 'NONE', 'UNVERIFIED');
CREATE TYPE session_session_skill_use_source_enum AS ENUM ('TOOL', 'COMMAND', 'PRELOADED');
CREATE TYPE toollink_tool_link_name_enum AS ENUM ('ROOT_AGENTS_FILE', 'CLAUDE_RULES', 'CLAUDE_TEMPLATES');
CREATE TYPE toolparameter_tool_parameter_format_enum AS ENUM ('JSON', 'YAML', 'TEXT');
CREATE TYPE version_version_source_enum AS ENUM ('MANUAL', 'CLI', 'SEED', 'ROLLBACK');
CREATE TYPE version_version_status_enum AS ENUM ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'SUPERSEDED');

-- =====================================================
-- 3. Create tables
-- =====================================================

CREATE TABLE kaya.accounts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    provider account_account_provider_enum NOT NULL,
    account_email VARCHAR(320) NOT NULL,
    account_organization_uuid VARCHAR(36),
    account_organization_name VARCHAR(200),
    billing_type VARCHAR(60),
    organization_type VARCHAR(60),
    seat_tier VARCHAR(60),
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounts_pk PRIMARY KEY (id),
    CONSTRAINT accounts_organization_id_provider_email_org_uuid_uk UNIQUE NULLS NOT DISTINCT (organization_id, provider, account_email, account_organization_uuid),
    CONSTRAINT accounts_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

CREATE TABLE kaya.account_users (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    account_id UUID NOT NULL,
    user_id UUID NOT NULL,
    source account_account_user_source_enum NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT account_users_pk PRIMARY KEY (id),
    CONSTRAINT account_users_account_id_user_id_uk UNIQUE (account_id, user_id),
    CONSTRAINT account_users_account_id_fk FOREIGN KEY (account_id) REFERENCES kaya.accounts(id) ON DELETE CASCADE,
    CONSTRAINT account_users_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT account_users_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id) ON DELETE CASCADE
);

CREATE TABLE kaya.agents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    machine_name VARCHAR(255) NOT NULL,
    claude_code_version VARCHAR(40),
    account_id UUID,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    status agent_agent_status_enum NOT NULL DEFAULT 'ACTIVE'::agent_agent_status_enum,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id UUID NOT NULL,
    CONSTRAINT agents_pk PRIMARY KEY (id),
    CONSTRAINT agents_organization_id_user_id_machine_name_uk UNIQUE (organization_id, user_id, machine_name),
    CONSTRAINT agents_account_id_fk FOREIGN KEY (account_id) REFERENCES kaya.accounts(id) ON DELETE SET NULL,
    CONSTRAINT agents_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT agents_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id) ON DELETE CASCADE
);

CREATE TABLE kaya.agent_tokens (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    agent_id UUID NOT NULL,
    token_prefix VARCHAR(12) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP + '365 days'::interval),
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id UUID NOT NULL,
    CONSTRAINT agent_tokens_pk PRIMARY KEY (id),
    CONSTRAINT agent_tokens_token_hash_uk UNIQUE (token_hash),
    CONSTRAINT agent_tokens_agent_id_fk FOREIGN KEY (agent_id) REFERENCES kaya.agents(id) ON DELETE CASCADE,
    CONSTRAINT agent_tokens_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id) ON DELETE CASCADE
);

CREATE TABLE kaya.agent_usage_snapshots (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    account_id UUID,
    session_id VARCHAR(100) NOT NULL,
    five_hour_used_percentage NUMERIC(5,2),
    five_hour_resets_at TIMESTAMP WITH TIME ZONE,
    seven_day_used_percentage NUMERIC(5,2),
    seven_day_resets_at TIMESTAMP WITH TIME ZONE,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT agent_usage_snapshots_pk PRIMARY KEY (id),
    CONSTRAINT agent_usage_snapshots_agent_id_uk UNIQUE (agent_id),
    CONSTRAINT agent_usage_snapshots_five_hour_used_percentage_ck CHECK (((five_hour_used_percentage >= (0)::numeric) AND (five_hour_used_percentage <= (100)::numeric))),
    CONSTRAINT agent_usage_snapshots_five_hour_window_ck CHECK (((five_hour_used_percentage IS NULL) = (five_hour_resets_at IS NULL))),
    CONSTRAINT agent_usage_snapshots_seven_day_used_percentage_ck CHECK (((seven_day_used_percentage >= (0)::numeric) AND (seven_day_used_percentage <= (100)::numeric))),
    CONSTRAINT agent_usage_snapshots_seven_day_window_ck CHECK (((seven_day_used_percentage IS NULL) = (seven_day_resets_at IS NULL))),
    CONSTRAINT agent_usage_snapshots_account_id_fk FOREIGN KEY (account_id) REFERENCES kaya.accounts(id) ON DELETE SET NULL,
    CONSTRAINT agent_usage_snapshots_agent_id_fk FOREIGN KEY (agent_id) REFERENCES kaya.agents(id) ON DELETE CASCADE,
    CONSTRAINT agent_usage_snapshots_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

CREATE TABLE kaya.blobs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    sha256 CHARACTER(64) NOT NULL,
    content bytea NOT NULL,
    size INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT blobs_pk PRIMARY KEY (id),
    CONSTRAINT blobs_organization_id_sha256_uk UNIQUE (organization_id, sha256),
    CONSTRAINT blobs_sha256_ck CHECK ((sha256 ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT blobs_size_ck CHECK ((((size >= 0) AND (size <= 5242880)) AND (size = octet_length(content)))),
    CONSTRAINT blobs_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

CREATE TABLE kaya.packs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name VARCHAR(60) NOT NULL,
    current_version_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    required BOOLEAN NOT NULL DEFAULT false,
    parent_pack_id UUID,
    description VARCHAR(1000),
    CONSTRAINT packs_pk PRIMARY KEY (id),
    CONSTRAINT packs_organization_id_name_uk UNIQUE (organization_id, name),
    CONSTRAINT packs_parent_pack_id_ck CHECK (((parent_pack_id IS NULL) OR (parent_pack_id <> id))),
    CONSTRAINT packs_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT packs_parent_pack_id_fk FOREIGN KEY (parent_pack_id) REFERENCES kaya.packs(id) ON DELETE RESTRICT
);

CREATE TABLE kaya.versions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    pack_id UUID NOT NULL,
    number INTEGER NOT NULL,
    status version_version_status_enum NOT NULL DEFAULT 'DRAFT'::version_version_status_enum,
    base_version_id UUID,
    proposed_by UUID NOT NULL,
    source version_version_source_enum NOT NULL,
    reason VARCHAR(1000),
    executable_change BOOLEAN NOT NULL DEFAULT false,
    manifest_sha256 VARCHAR(64),
    submitted_at TIMESTAMP WITH TIME ZONE,
    decided_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT versions_pk PRIMARY KEY (id),
    CONSTRAINT versions_number_ck CHECK ((number >= 1)),
    CONSTRAINT versions_base_version_id_fk FOREIGN KEY (base_version_id) REFERENCES kaya.versions(id) ON DELETE SET NULL,
    CONSTRAINT versions_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT versions_pack_id_fk FOREIGN KEY (pack_id) REFERENCES kaya.packs(id) ON DELETE CASCADE,
    CONSTRAINT versions_proposed_by_fk FOREIGN KEY (proposed_by) REFERENCES shrine.users(id)
);

CREATE TABLE kaya.agents_files (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    placement agentsfile_agents_file_placement_enum NOT NULL,
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT agents_files_pk PRIMARY KEY (id),
    CONSTRAINT agents_files_version_id_placement_uk UNIQUE (version_id, placement),
    CONSTRAINT agents_files_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT agents_files_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT agents_files_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.conventions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    relative_path VARCHAR(500),
    title VARCHAR(300),
    overrides_path VARCHAR(500),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT conventions_pk PRIMARY KEY (id),
    CONSTRAINT conventions_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT conventions_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT conventions_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.guardrails (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(60) NOT NULL,
    category guardrail_guardrail_category_enum NOT NULL,
    description VARCHAR(4000),
    deny JSONB NOT NULL DEFAULT '[]'::jsonb,
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT guardrails_pk PRIMARY KEY (id),
    CONSTRAINT guardrails_version_id_name_uk UNIQUE (version_id, name),
    CONSTRAINT guardrails_deny_ck CHECK (((jsonb_typeof(deny) = 'array'::text) AND (jsonb_array_length(deny) <= 20))),
    CONSTRAINT guardrails_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT guardrails_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT guardrails_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.hooks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    relative_path VARCHAR(500) NOT NULL,
    executable BOOLEAN NOT NULL DEFAULT false,
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hooks_pk PRIMARY KEY (id),
    CONSTRAINT hooks_version_id_relative_path_uk UNIQUE (version_id, relative_path),
    CONSTRAINT hooks_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT hooks_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT hooks_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.user_audit_entries (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    actor_kind identity_user_audit_entry_actor_kind_enum NOT NULL,
    actor_id UUID,
    action identity_user_audit_entry_action_enum NOT NULL,
    reason VARCHAR(1000),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_audit_entries_pk PRIMARY KEY (id),
    CONSTRAINT user_audit_entries_details_ck CHECK ((jsonb_typeof(details) = 'object'::text)),
    CONSTRAINT user_audit_entries_actor_id_fk FOREIGN KEY (actor_id) REFERENCES shrine.users(id) ON DELETE SET NULL,
    CONSTRAINT user_audit_entries_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT user_audit_entries_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id) ON DELETE CASCADE
);

CREATE TABLE kaya.user_git_emails (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    source identity_user_git_email_source_enum NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_git_emails_pk PRIMARY KEY (id),
    CONSTRAINT user_git_emails_organization_id_email_uk UNIQUE (organization_id, email),
    CONSTRAINT user_git_emails_email_ck CHECK (((email)::text = lower((email)::text))),
    CONSTRAINT user_git_emails_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT user_git_emails_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id) ON DELETE CASCADE
);

CREATE TABLE kaya.repositories (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    remote_url VARCHAR(500) NOT NULL,
    ticket_url_template VARCHAR(500),
    status repository_repository_status_enum NOT NULL DEFAULT 'ACTIVE'::repository_repository_status_enum,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT repositories_pk PRIMARY KEY (id),
    CONSTRAINT repositories_organization_id_remote_url_uk UNIQUE (organization_id, remote_url),
    CONSTRAINT repositories_ticket_url_template_ck CHECK (((ticket_url_template IS NULL) OR (POSITION(('{key}'::text) IN (ticket_url_template)) > 0))),
    CONSTRAINT repositories_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

CREATE TABLE kaya.lessons (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    claude_session_id VARCHAR(100),
    organization_id UUID NOT NULL,
    repository_id UUID,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT lessons_pk PRIMARY KEY (id),
    CONSTRAINT lessons_body_ck CHECK (((char_length(body) >= 1) AND (char_length(body) <= 4000))),
    CONSTRAINT lessons_title_ck CHECK (((char_length((title)::text) >= 1) AND (char_length((title)::text) <= 300))),
    CONSTRAINT lessons_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT lessons_repository_id_fk FOREIGN KEY (repository_id) REFERENCES kaya.repositories(id) ON DELETE CASCADE,
    CONSTRAINT lessons_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id)
);

CREATE TABLE kaya.releases (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL,
    number INTEGER NOT NULL,
    cause_version_id UUID NOT NULL,
    manifest JSONB NOT NULL,
    manifest_sha256 VARCHAR(64) NOT NULL,
    signature VARCHAR(88) NOT NULL,
    key_id VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pack_versions JSONB NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT releases_pk PRIMARY KEY (id),
    CONSTRAINT releases_repository_id_number_uk UNIQUE (repository_id, number),
    CONSTRAINT releases_manifest_ck CHECK ((jsonb_typeof(manifest) = 'array'::text)),
    CONSTRAINT releases_number_ck CHECK ((number >= 1)),
    CONSTRAINT releases_pack_versions_ck CHECK ((jsonb_typeof(pack_versions) = 'array'::text)),
    CONSTRAINT releases_cause_version_id_fk FOREIGN KEY (cause_version_id) REFERENCES kaya.versions(id),
    CONSTRAINT releases_repository_id_fk FOREIGN KEY (repository_id) REFERENCES kaya.repositories(id) ON DELETE CASCADE
);

CREATE TABLE kaya.sessions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    session_title VARCHAR(200),
    first_prompt VARCHAR(500),
    branch VARCHAR(255),
    cwd VARCHAR(500),
    model VARCHAR(100),
    claude_code_version VARCHAR(40),
    account_email VARCHAR(320),
    account_organization_uuid VARCHAR(36),
    account_organization_name VARCHAR(200),
    billing_type VARCHAR(60),
    seat_tier VARCHAR(60),
    git_author_email VARCHAR(320),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_event_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended BOOLEAN NOT NULL DEFAULT false,
    event_count INTEGER NOT NULL DEFAULT 0,
    features JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    release_id UUID,
    release_state session_session_release_state_enum,
    delivered_by session_session_delivered_by_enum,
    user_id UUID NOT NULL,
    identity_conflict_user_id UUID,
    CONSTRAINT sessions_pk PRIMARY KEY (id),
    CONSTRAINT sessions_organization_id_session_id_uk UNIQUE (organization_id, session_id),
    CONSTRAINT sessions_event_count_ck CHECK ((event_count >= 0)),
    CONSTRAINT sessions_agent_id_fk FOREIGN KEY (agent_id) REFERENCES kaya.agents(id),
    CONSTRAINT sessions_identity_conflict_user_id_fk FOREIGN KEY (identity_conflict_user_id) REFERENCES shrine.users(id) ON DELETE SET NULL,
    CONSTRAINT sessions_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT sessions_release_id_fk FOREIGN KEY (release_id) REFERENCES kaya.releases(id) ON DELETE SET NULL,
    CONSTRAINT sessions_repository_id_fk FOREIGN KEY (repository_id) REFERENCES kaya.repositories(id),
    CONSTRAINT sessions_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id)
);

CREATE TABLE kaya.plans (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    repository_id UUID NOT NULL,
    kind plan_plan_kind_enum NOT NULL,
    path VARCHAR(500),
    session_id UUID,
    title VARCHAR(300) NOT NULL,
    feature VARCHAR(100),
    review_state plan_plan_review_state_enum NOT NULL DEFAULT 'OPEN'::plan_plan_review_state_enum,
    latest_version_number INTEGER NOT NULL DEFAULT 0,
    latest_session_id UUID,
    open_feedback_count INTEGER NOT NULL DEFAULT 0,
    last_changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id UUID NOT NULL,
    pack_id UUID,
    CONSTRAINT plans_pk PRIMARY KEY (id),
    CONSTRAINT plans_kind_ck CHECK ((((kind = 'SPEC'::plan_plan_kind_enum) AND (path IS NOT NULL) AND (session_id IS NULL)) OR ((kind = 'SESSION'::plan_plan_kind_enum) AND (path IS NULL) AND (session_id IS NOT NULL)))),
    CONSTRAINT plans_latest_version_number_ck CHECK ((latest_version_number >= 0)),
    CONSTRAINT plans_open_feedback_count_ck CHECK ((open_feedback_count >= 0)),
    CONSTRAINT plans_path_ck CHECK (((path IS NULL) OR (char_length((path)::text) >= 1))),
    CONSTRAINT plans_latest_session_id_fk FOREIGN KEY (latest_session_id) REFERENCES kaya.sessions(id) ON DELETE SET NULL,
    CONSTRAINT plans_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT plans_pack_id_fk FOREIGN KEY (pack_id) REFERENCES kaya.packs(id) ON DELETE SET NULL,
    CONSTRAINT plans_repository_id_fk FOREIGN KEY (repository_id) REFERENCES kaya.repositories(id) ON DELETE CASCADE,
    CONSTRAINT plans_session_id_fk FOREIGN KEY (session_id) REFERENCES kaya.sessions(id) ON DELETE CASCADE,
    CONSTRAINT plans_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id)
);

CREATE TABLE kaya.plan_versions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL,
    number INTEGER NOT NULL,
    source plan_plan_version_source_enum NOT NULL,
    session_id UUID,
    content TEXT NOT NULL,
    content_sha256 CHARACTER(64) NOT NULL,
    line_count INTEGER NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT plan_versions_pk PRIMARY KEY (id),
    CONSTRAINT plan_versions_plan_id_number_uk UNIQUE (plan_id, number),
    CONSTRAINT plan_versions_content_ck CHECK (((char_length(content) >= 1) AND (char_length(content) <= 200000))),
    CONSTRAINT plan_versions_line_count_ck CHECK ((line_count >= 1)),
    CONSTRAINT plan_versions_number_ck CHECK ((number >= 1)),
    CONSTRAINT plan_versions_plan_id_fk FOREIGN KEY (plan_id) REFERENCES kaya.plans(id) ON DELETE CASCADE,
    CONSTRAINT plan_versions_session_id_fk FOREIGN KEY (session_id) REFERENCES kaya.sessions(id) ON DELETE SET NULL
);

CREATE TABLE kaya.plan_feedback (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL,
    version_id UUID NOT NULL,
    reviewer_id UUID NOT NULL,
    decision plan_plan_feedback_decision_enum NOT NULL,
    comment VARCHAR(4000),
    line_start INTEGER,
    line_end INTEGER,
    quote VARCHAR(500),
    delivered_at TIMESTAMP WITH TIME ZONE,
    delivered_session_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT plan_feedback_pk PRIMARY KEY (id),
    CONSTRAINT plan_feedback_comment_ck CHECK ((((comment IS NULL) AND (decision = 'APPROVE'::plan_plan_feedback_decision_enum)) OR ((comment IS NOT NULL) AND ((char_length((comment)::text) >= 1) AND (char_length((comment)::text) <= 4000))))),
    CONSTRAINT plan_feedback_delivered_ck CHECK ((((delivered_at IS NULL) AND (delivered_session_id IS NULL)) OR ((delivered_at IS NOT NULL) AND (delivered_session_id IS NOT NULL)))),
    CONSTRAINT plan_feedback_lines_ck CHECK ((((line_start IS NULL) AND (line_end IS NULL)) OR ((line_start IS NOT NULL) AND (line_end IS NOT NULL) AND (line_start >= 1) AND (line_start <= line_end)))),
    CONSTRAINT plan_feedback_plan_id_fk FOREIGN KEY (plan_id) REFERENCES kaya.plans(id) ON DELETE CASCADE,
    CONSTRAINT plan_feedback_reviewer_id_fk FOREIGN KEY (reviewer_id) REFERENCES shrine.users(id),
    CONSTRAINT plan_feedback_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.plan_versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.reference_files (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(300) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reference_files_pk PRIMARY KEY (id),
    CONSTRAINT reference_files_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT reference_files_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT reference_files_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.repository_packs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL,
    pack_id UUID NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    root_path VARCHAR(200),
    CONSTRAINT repository_packs_pk PRIMARY KEY (id),
    CONSTRAINT repository_packs_repository_id_pack_id_uk UNIQUE (repository_id, pack_id),
    CONSTRAINT repository_packs_position_ck CHECK (("position" >= 0)),
    CONSTRAINT repository_packs_root_path_ck CHECK (((root_path IS NULL) OR ((root_path)::text ~ '^[A-Za-z0-9_][A-Za-z0-9._-]*(/[A-Za-z0-9_][A-Za-z0-9._-]*){0,3}$'::text))),
    CONSTRAINT repository_packs_pack_id_fk FOREIGN KEY (pack_id) REFERENCES kaya.packs(id) ON DELETE CASCADE,
    CONSTRAINT repository_packs_repository_id_fk FOREIGN KEY (repository_id) REFERENCES kaya.repositories(id) ON DELETE CASCADE
);

CREATE TABLE kaya.reviews (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    version_id UUID NOT NULL,
    reviewer_id UUID NOT NULL,
    decision review_review_decision_enum NOT NULL,
    comment VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reviews_pk PRIMARY KEY (id),
    CONSTRAINT reviews_comment_ck CHECK (((comment IS NULL) OR (char_length((comment)::text) >= 1))),
    CONSTRAINT reviews_reviewer_id_fk FOREIGN KEY (reviewer_id) REFERENCES shrine.users(id),
    CONSTRAINT reviews_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.roles (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    relative_path VARCHAR(500) NOT NULL,
    title VARCHAR(300),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT roles_pk PRIMARY KEY (id),
    CONSTRAINT roles_version_id_relative_path_uk UNIQUE (version_id, relative_path),
    CONSTRAINT roles_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT roles_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT roles_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.rules (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    relative_path VARCHAR(500) NOT NULL,
    description VARCHAR(4000),
    paths JSONB NOT NULL DEFAULT '[]'::jsonb,
    always_on BOOLEAN NOT NULL,
    overrides_path VARCHAR(500),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rules_pk PRIMARY KEY (id),
    CONSTRAINT rules_version_id_relative_path_uk UNIQUE (version_id, relative_path),
    CONSTRAINT rules_always_on_ck CHECK ((always_on = (jsonb_array_length(paths) = 0))),
    CONSTRAINT rules_paths_ck CHECK ((jsonb_typeof(paths) = 'array'::text)),
    CONSTRAINT rules_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT rules_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT rules_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.rule_conventions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    rule_id UUID NOT NULL,
    convention_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rule_conventions_pk PRIMARY KEY (id),
    CONSTRAINT rule_conventions_rule_id_convention_path_uk UNIQUE (rule_id, convention_path),
    CONSTRAINT rule_conventions_rule_id_fk FOREIGN KEY (rule_id) REFERENCES kaya.rules(id) ON DELETE CASCADE
);

CREATE TABLE kaya.scripts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(300) NOT NULL,
    executable BOOLEAN NOT NULL DEFAULT false,
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT scripts_pk PRIMARY KEY (id),
    CONSTRAINT scripts_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT scripts_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT scripts_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.seed_runs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    source_ref VARCHAR(100) NOT NULL,
    status seed_seed_run_status_enum NOT NULL,
    report JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT seed_runs_pk PRIMARY KEY (id),
    CONSTRAINT seed_runs_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT seed_runs_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id)
);

CREATE TABLE kaya.session_events (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    seq BIGINT NOT NULL,
    at TIMESTAMP WITH TIME ZONE NOT NULL,
    event VARCHAR(40) NOT NULL,
    agent_id VARCHAR(100),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_events_pk PRIMARY KEY (id),
    CONSTRAINT session_events_session_id_seq_uk UNIQUE (session_id, seq),
    CONSTRAINT session_events_seq_ck CHECK ((seq >= 1)),
    CONSTRAINT session_events_session_id_fk FOREIGN KEY (session_id) REFERENCES kaya.sessions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.session_handoff_snapshots (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    path VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_handoff_snapshots_pk PRIMARY KEY (id),
    CONSTRAINT session_handoff_snapshots_session_id_path_uk UNIQUE (session_id, path),
    CONSTRAINT session_handoff_snapshots_content_ck CHECK ((char_length(content) <= 200000)),
    CONSTRAINT session_handoff_snapshots_session_id_fk FOREIGN KEY (session_id) REFERENCES kaya.sessions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.session_links (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    kind session_session_link_kind_enum NOT NULL,
    reference VARCHAR(500) NOT NULL,
    url VARCHAR(500),
    source session_session_link_source_enum NOT NULL,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_links_pk PRIMARY KEY (id),
    CONSTRAINT session_links_session_id_kind_reference_uk UNIQUE (session_id, kind, reference),
    CONSTRAINT session_links_created_by_fk FOREIGN KEY (created_by) REFERENCES shrine.users(id) ON DELETE SET NULL,
    CONSTRAINT session_links_session_id_fk FOREIGN KEY (session_id) REFERENCES kaya.sessions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.session_skill_uses (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    skill VARCHAR(100) NOT NULL,
    source session_session_skill_use_source_enum NOT NULL,
    use_count INTEGER NOT NULL DEFAULT 1,
    first_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_skill_uses_pk PRIMARY KEY (id),
    CONSTRAINT session_skill_uses_session_id_skill_source_uk UNIQUE (session_id, skill, source),
    CONSTRAINT session_skill_uses_use_count_ck CHECK ((use_count >= 1)),
    CONSTRAINT session_skill_uses_session_id_fk FOREIGN KEY (session_id) REFERENCES kaya.sessions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.session_subagents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    agent_id VARCHAR(100) NOT NULL,
    subagent_type VARCHAR(100) NOT NULL,
    model VARCHAR(100),
    description VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_subagents_pk PRIMARY KEY (id),
    CONSTRAINT session_subagents_session_id_agent_id_uk UNIQUE (session_id, agent_id),
    CONSTRAINT session_subagents_session_id_fk FOREIGN KEY (session_id) REFERENCES kaya.sessions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.settings (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT settings_pk PRIMARY KEY (id),
    CONSTRAINT settings_version_id_uk UNIQUE (version_id),
    CONSTRAINT settings_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT settings_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT settings_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.skills (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(4000),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT skills_pk PRIMARY KEY (id),
    CONSTRAINT skills_version_id_name_uk UNIQUE (version_id, name),
    CONSTRAINT skills_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT skills_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT skills_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.skill_conventions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    skill_id UUID NOT NULL,
    convention_id UUID NOT NULL,
    path VARCHAR(300) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT skill_conventions_pk PRIMARY KEY (id),
    CONSTRAINT skill_conventions_skill_id_convention_id_uk UNIQUE (skill_id, convention_id),
    CONSTRAINT skill_conventions_skill_id_path_uk UNIQUE (skill_id, path),
    CONSTRAINT skill_conventions_convention_id_fk FOREIGN KEY (convention_id) REFERENCES kaya.conventions(id) ON DELETE CASCADE,
    CONSTRAINT skill_conventions_skill_id_fk FOREIGN KEY (skill_id) REFERENCES kaya.skills(id) ON DELETE CASCADE
);

CREATE TABLE kaya.skill_references (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    skill_id UUID NOT NULL,
    reference_id UUID NOT NULL,
    path VARCHAR(300) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT skill_references_pk PRIMARY KEY (id),
    CONSTRAINT skill_references_skill_id_path_uk UNIQUE (skill_id, path),
    CONSTRAINT skill_references_skill_id_reference_id_uk UNIQUE (skill_id, reference_id),
    CONSTRAINT skill_references_reference_id_fk FOREIGN KEY (reference_id) REFERENCES kaya.reference_files(id) ON DELETE CASCADE,
    CONSTRAINT skill_references_skill_id_fk FOREIGN KEY (skill_id) REFERENCES kaya.skills(id) ON DELETE CASCADE
);

CREATE TABLE kaya.skill_scripts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    skill_id UUID NOT NULL,
    script_id UUID NOT NULL,
    path VARCHAR(300) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT skill_scripts_pk PRIMARY KEY (id),
    CONSTRAINT skill_scripts_skill_id_path_uk UNIQUE (skill_id, path),
    CONSTRAINT skill_scripts_skill_id_script_id_uk UNIQUE (skill_id, script_id),
    CONSTRAINT skill_scripts_script_id_fk FOREIGN KEY (script_id) REFERENCES kaya.scripts(id) ON DELETE CASCADE,
    CONSTRAINT skill_scripts_skill_id_fk FOREIGN KEY (skill_id) REFERENCES kaya.skills(id) ON DELETE CASCADE
);

CREATE TABLE kaya.tool_parameters (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(300) NOT NULL,
    format toolparameter_tool_parameter_format_enum NOT NULL,
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tool_parameters_pk PRIMARY KEY (id),
    CONSTRAINT tool_parameters_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT tool_parameters_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT tool_parameters_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.skill_tool_parameters (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    skill_id UUID NOT NULL,
    tool_parameter_id UUID NOT NULL,
    path VARCHAR(300) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT skill_tool_parameters_pk PRIMARY KEY (id),
    CONSTRAINT skill_tool_parameters_skill_id_path_uk UNIQUE (skill_id, path),
    CONSTRAINT skill_tool_parameters_skill_id_tool_parameter_id_uk UNIQUE (skill_id, tool_parameter_id),
    CONSTRAINT skill_tool_parameters_skill_id_fk FOREIGN KEY (skill_id) REFERENCES kaya.skills(id) ON DELETE CASCADE,
    CONSTRAINT skill_tool_parameters_tool_parameter_id_fk FOREIGN KEY (tool_parameter_id) REFERENCES kaya.tool_parameters(id) ON DELETE CASCADE
);

CREATE TABLE kaya.workflows (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    relative_path VARCHAR(500),
    title VARCHAR(300),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workflows_pk PRIMARY KEY (id),
    CONSTRAINT workflows_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT workflows_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT workflows_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.skill_workflows (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    skill_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    path VARCHAR(300) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT skill_workflows_pk PRIMARY KEY (id),
    CONSTRAINT skill_workflows_skill_id_path_uk UNIQUE (skill_id, path),
    CONSTRAINT skill_workflows_skill_id_workflow_id_uk UNIQUE (skill_id, workflow_id),
    CONSTRAINT skill_workflows_skill_id_fk FOREIGN KEY (skill_id) REFERENCES kaya.skills(id) ON DELETE CASCADE,
    CONSTRAINT skill_workflows_workflow_id_fk FOREIGN KEY (workflow_id) REFERENCES kaya.workflows(id) ON DELETE CASCADE
);

CREATE TABLE kaya.subagents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    relative_path VARCHAR(500) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(4000),
    model VARCHAR(100),
    max_turns INTEGER,
    tools JSONB,
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT subagents_pk PRIMARY KEY (id),
    CONSTRAINT subagents_version_id_name_uk UNIQUE (version_id, name),
    CONSTRAINT subagents_version_id_relative_path_uk UNIQUE (version_id, relative_path),
    CONSTRAINT subagents_max_turns_ck CHECK (((max_turns IS NULL) OR (max_turns >= 1))),
    CONSTRAINT subagents_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT subagents_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT subagents_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.templates (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    relative_path VARCHAR(500) NOT NULL,
    title VARCHAR(300),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    content_blob_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT templates_pk PRIMARY KEY (id),
    CONSTRAINT templates_version_id_relative_path_uk UNIQUE (version_id, relative_path),
    CONSTRAINT templates_content_blob_id_fk FOREIGN KEY (content_blob_id) REFERENCES kaya.blobs(id),
    CONSTRAINT templates_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT templates_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.tool_links (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    name toollink_tool_link_name_enum NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tool_links_pk PRIMARY KEY (id),
    CONSTRAINT tool_links_version_id_name_uk UNIQUE (version_id, name),
    CONSTRAINT tool_links_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT tool_links_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

CREATE TABLE kaya.item_overrides (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    version_id UUID NOT NULL,
    path VARCHAR(500) NOT NULL,
    base_pack_id UUID NOT NULL,
    base_version_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT item_overrides_pk PRIMARY KEY (id),
    CONSTRAINT item_overrides_version_id_path_uk UNIQUE (version_id, path),
    CONSTRAINT item_overrides_base_pack_id_fk FOREIGN KEY (base_pack_id) REFERENCES kaya.packs(id) ON DELETE CASCADE,
    CONSTRAINT item_overrides_base_version_id_fk FOREIGN KEY (base_version_id) REFERENCES kaya.versions(id) ON DELETE SET NULL,
    CONSTRAINT item_overrides_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT item_overrides_version_id_fk FOREIGN KEY (version_id) REFERENCES kaya.versions(id) ON DELETE CASCADE
);

-- =====================================================
-- 4. Add foreign keys that point to a table created later
-- =====================================================

ALTER TABLE kaya.packs
    ADD CONSTRAINT packs_current_version_id_fk FOREIGN KEY (current_version_id) REFERENCES kaya.versions(id) ON DELETE SET NULL;

-- =====================================================
-- 5. Create indexes
-- =====================================================

CREATE INDEX account_users_organization_id_idx ON kaya.account_users USING btree (organization_id);
CREATE INDEX account_users_user_id_idx ON kaya.account_users USING btree (user_id);
CREATE INDEX agents_account_id_idx ON kaya.agents USING btree (account_id);
CREATE INDEX agents_user_id_idx ON kaya.agents USING btree (user_id);
CREATE UNIQUE INDEX agent_tokens_agent_id_active_uk ON kaya.agent_tokens USING btree (agent_id) WHERE (revoked_at IS NULL);
CREATE INDEX agent_tokens_agent_id_idx ON kaya.agent_tokens USING btree (agent_id);
CREATE INDEX agent_tokens_user_id_idx ON kaya.agent_tokens USING btree (user_id);
CREATE INDEX agent_usage_snapshots_account_id_captured_at_idx ON kaya.agent_usage_snapshots USING btree (account_id, captured_at DESC);
CREATE INDEX agent_usage_snapshots_organization_id_idx ON kaya.agent_usage_snapshots USING btree (organization_id);
CREATE INDEX packs_current_version_id_idx ON kaya.packs USING btree (current_version_id);
CREATE INDEX packs_parent_pack_id_idx ON kaya.packs USING btree (parent_pack_id);
CREATE INDEX versions_base_version_id_idx ON kaya.versions USING btree (base_version_id);
CREATE INDEX versions_organization_id_idx ON kaya.versions USING btree (organization_id);
CREATE UNIQUE INDEX versions_pack_id_in_review_uk ON kaya.versions USING btree (pack_id) WHERE ((status = 'IN_REVIEW'::version_version_status_enum) AND (pack_id IS NOT NULL));
CREATE UNIQUE INDEX versions_pack_id_number_uk ON kaya.versions USING btree (pack_id, number) WHERE (pack_id IS NOT NULL);
CREATE INDEX versions_proposed_by_idx ON kaya.versions USING btree (proposed_by);
CREATE INDEX agents_files_content_blob_id_idx ON kaya.agents_files USING btree (content_blob_id);
CREATE INDEX agents_files_organization_id_idx ON kaya.agents_files USING btree (organization_id);
CREATE INDEX agents_files_version_id_idx ON kaya.agents_files USING btree (version_id);
CREATE INDEX conventions_content_blob_id_idx ON kaya.conventions USING btree (content_blob_id);
CREATE INDEX conventions_organization_id_idx ON kaya.conventions USING btree (organization_id);
CREATE INDEX conventions_version_id_idx ON kaya.conventions USING btree (version_id);
CREATE UNIQUE INDEX conventions_version_id_relative_path_uk ON kaya.conventions USING btree (version_id, relative_path) WHERE (relative_path IS NOT NULL);
CREATE INDEX guardrails_content_blob_id_idx ON kaya.guardrails USING btree (content_blob_id);
CREATE INDEX guardrails_organization_id_idx ON kaya.guardrails USING btree (organization_id);
CREATE INDEX guardrails_version_id_idx ON kaya.guardrails USING btree (version_id);
CREATE INDEX hooks_content_blob_id_idx ON kaya.hooks USING btree (content_blob_id);
CREATE INDEX hooks_organization_id_idx ON kaya.hooks USING btree (organization_id);
CREATE INDEX hooks_version_id_idx ON kaya.hooks USING btree (version_id);
CREATE INDEX user_audit_entries_actor_id_idx ON kaya.user_audit_entries USING btree (actor_id);
CREATE INDEX user_audit_entries_organization_id_idx ON kaya.user_audit_entries USING btree (organization_id);
CREATE INDEX user_audit_entries_user_id_created_at_idx ON kaya.user_audit_entries USING btree (user_id, created_at DESC);
CREATE INDEX user_git_emails_user_id_idx ON kaya.user_git_emails USING btree (user_id);
CREATE INDEX lessons_organization_id_claude_session_id_idx ON kaya.lessons USING btree (organization_id, claude_session_id);
CREATE INDEX lessons_organization_id_repository_id_idx ON kaya.lessons USING btree (organization_id, repository_id);
CREATE INDEX lessons_repository_id_idx ON kaya.lessons USING btree (repository_id);
CREATE INDEX lessons_user_id_idx ON kaya.lessons USING btree (user_id);
CREATE INDEX releases_cause_version_id_idx ON kaya.releases USING btree (cause_version_id);
CREATE INDEX sessions_account_organization_uuid_idx ON kaya.sessions USING btree (account_organization_uuid);
CREATE INDEX sessions_agent_id_idx ON kaya.sessions USING btree (agent_id);
CREATE INDEX sessions_identity_conflict_user_id_idx ON kaya.sessions USING btree (identity_conflict_user_id);
CREATE INDEX sessions_model_idx ON kaya.sessions USING btree (model);
CREATE INDEX sessions_organization_id_last_event_at_idx ON kaya.sessions USING btree (organization_id, last_event_at DESC);
CREATE INDEX sessions_release_id_idx ON kaya.sessions USING btree (release_id);
CREATE INDEX sessions_repository_id_idx ON kaya.sessions USING btree (repository_id);
CREATE INDEX sessions_user_id_idx ON kaya.sessions USING btree (user_id);
CREATE INDEX plans_latest_session_id_idx ON kaya.plans USING btree (latest_session_id);
CREATE INDEX plans_organization_id_last_changed_at_idx ON kaya.plans USING btree (organization_id, last_changed_at DESC);
CREATE INDEX plans_pack_id_idx ON kaya.plans USING btree (pack_id);
CREATE INDEX plans_repository_id_idx ON kaya.plans USING btree (repository_id);
CREATE UNIQUE INDEX plans_repository_id_path_uk ON kaya.plans USING btree (repository_id, path) WHERE (kind = 'SPEC'::plan_plan_kind_enum);
CREATE UNIQUE INDEX plans_session_id_uk ON kaya.plans USING btree (session_id) WHERE (kind = 'SESSION'::plan_plan_kind_enum);
CREATE INDEX plans_user_id_idx ON kaya.plans USING btree (user_id);
CREATE INDEX plan_versions_session_id_idx ON kaya.plan_versions USING btree (session_id);
CREATE INDEX plan_feedback_pending_idx ON kaya.plan_feedback USING btree (plan_id) WHERE (delivered_at IS NULL);
CREATE INDEX plan_feedback_plan_id_created_at_idx ON kaya.plan_feedback USING btree (plan_id, created_at);
CREATE INDEX plan_feedback_reviewer_id_idx ON kaya.plan_feedback USING btree (reviewer_id);
CREATE INDEX plan_feedback_version_id_idx ON kaya.plan_feedback USING btree (version_id);
CREATE INDEX reference_files_content_blob_id_idx ON kaya.reference_files USING btree (content_blob_id);
CREATE INDEX reference_files_organization_id_idx ON kaya.reference_files USING btree (organization_id);
CREATE INDEX reference_files_version_id_idx ON kaya.reference_files USING btree (version_id);
CREATE INDEX repository_packs_pack_id_idx ON kaya.repository_packs USING btree (pack_id);
CREATE UNIQUE INDEX repository_packs_repository_id_root_path_uk ON kaya.repository_packs USING btree (repository_id, root_path) WHERE (root_path IS NOT NULL);
CREATE INDEX reviews_reviewer_id_idx ON kaya.reviews USING btree (reviewer_id);
CREATE INDEX reviews_version_id_created_at_idx ON kaya.reviews USING btree (version_id, created_at);
CREATE INDEX roles_content_blob_id_idx ON kaya.roles USING btree (content_blob_id);
CREATE INDEX roles_organization_id_idx ON kaya.roles USING btree (organization_id);
CREATE INDEX roles_version_id_idx ON kaya.roles USING btree (version_id);
CREATE INDEX rules_content_blob_id_idx ON kaya.rules USING btree (content_blob_id);
CREATE INDEX rules_organization_id_idx ON kaya.rules USING btree (organization_id);
CREATE INDEX rules_version_id_idx ON kaya.rules USING btree (version_id);
CREATE INDEX scripts_content_blob_id_idx ON kaya.scripts USING btree (content_blob_id);
CREATE INDEX scripts_organization_id_idx ON kaya.scripts USING btree (organization_id);
CREATE INDEX scripts_version_id_idx ON kaya.scripts USING btree (version_id);
CREATE INDEX seed_runs_organization_id_created_at_idx ON kaya.seed_runs USING btree (organization_id, created_at DESC);
CREATE INDEX seed_runs_user_id_idx ON kaya.seed_runs USING btree (user_id);
CREATE INDEX session_links_created_by_idx ON kaya.session_links USING btree (created_by);
CREATE INDEX settings_content_blob_id_idx ON kaya.settings USING btree (content_blob_id);
CREATE INDEX settings_organization_id_idx ON kaya.settings USING btree (organization_id);
CREATE INDEX settings_version_id_idx ON kaya.settings USING btree (version_id);
CREATE INDEX skills_content_blob_id_idx ON kaya.skills USING btree (content_blob_id);
CREATE INDEX skills_organization_id_idx ON kaya.skills USING btree (organization_id);
CREATE INDEX skills_version_id_idx ON kaya.skills USING btree (version_id);
CREATE INDEX skill_conventions_convention_id_idx ON kaya.skill_conventions USING btree (convention_id);
CREATE INDEX skill_references_reference_id_idx ON kaya.skill_references USING btree (reference_id);
CREATE INDEX skill_scripts_script_id_idx ON kaya.skill_scripts USING btree (script_id);
CREATE INDEX tool_parameters_content_blob_id_idx ON kaya.tool_parameters USING btree (content_blob_id);
CREATE INDEX tool_parameters_organization_id_idx ON kaya.tool_parameters USING btree (organization_id);
CREATE INDEX tool_parameters_version_id_idx ON kaya.tool_parameters USING btree (version_id);
CREATE INDEX skill_tool_parameters_tool_parameter_id_idx ON kaya.skill_tool_parameters USING btree (tool_parameter_id);
CREATE INDEX workflows_content_blob_id_idx ON kaya.workflows USING btree (content_blob_id);
CREATE INDEX workflows_organization_id_idx ON kaya.workflows USING btree (organization_id);
CREATE INDEX workflows_version_id_idx ON kaya.workflows USING btree (version_id);
CREATE UNIQUE INDEX workflows_version_id_relative_path_uk ON kaya.workflows USING btree (version_id, relative_path) WHERE (relative_path IS NOT NULL);
CREATE INDEX skill_workflows_workflow_id_idx ON kaya.skill_workflows USING btree (workflow_id);
CREATE INDEX subagents_content_blob_id_idx ON kaya.subagents USING btree (content_blob_id);
CREATE INDEX subagents_organization_id_idx ON kaya.subagents USING btree (organization_id);
CREATE INDEX subagents_version_id_idx ON kaya.subagents USING btree (version_id);
CREATE INDEX templates_content_blob_id_idx ON kaya.templates USING btree (content_blob_id);
CREATE INDEX templates_organization_id_idx ON kaya.templates USING btree (organization_id);
CREATE INDEX templates_version_id_idx ON kaya.templates USING btree (version_id);
CREATE INDEX tool_links_organization_id_idx ON kaya.tool_links USING btree (organization_id);
CREATE INDEX item_overrides_base_pack_id_idx ON kaya.item_overrides USING btree (base_pack_id);
CREATE INDEX item_overrides_base_version_id_idx ON kaya.item_overrides USING btree (base_version_id);
CREATE INDEX item_overrides_organization_id_idx ON kaya.item_overrides USING btree (organization_id);
