-- Village Database Schema - Initial Schema
-- V1_0_0__Initial_Schema.sql
--
-- Establishes the complete initial database schema for the Village platform,
-- covering all domains: shrine (identity, organization),
-- kaizen (boards, sprints, tickets), and lancer (Kubernetes workload log streaming).

-- =====================================================
-- Conventions
-- Enums: {schema}_{domain}_{enum_class_name_snake_case}_enum
-- Tables: snake_case plural
-- Primary keys: {table}_pk
-- Foreign keys: {table}_{column}_fk
-- Unique keys: {table}_{column(s)}_uk
-- Indexes: {table}_{column(s)}_idx
-- =====================================================

-- =====================================================
-- 1. Schemas
-- =====================================================
CREATE SCHEMA shrine;

CREATE SCHEMA kaizen;

CREATE SCHEMA lancer;

CREATE SCHEMA senman;

CREATE SCHEMA specter;

CREATE SCHEMA studio;

-- =====================================================
-- 2. Enum types
-- =====================================================

------Shrine-------
-- Identity domain
CREATE TYPE shrine_identity_user_status_enum AS ENUM ('PENDING_ONBOARDING', 'ACTIVE', 'DELETED');

CREATE TYPE shrine_identity_user_invitation_status_enum AS ENUM ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED');

CREATE TYPE shrine_identity_user_code_type_enum AS ENUM ('ORGANIZATION_INVITATION', 'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'PASSCHAT_SHARE');

CREATE TYPE shrine_identity_user_position_enum AS ENUM ('PROCUREMENT', 'SALES', 'PRODUCTION', 'MARKETING', 'OFFICE', 'LOGISTIC', 'WAREHOUSE', 'MANAGEMENT', 'ADMINISTRATION', 'ICT', 'RESEARCH_AND_DEVELOPMENT', 'OTHER');

CREATE TYPE shrine_identity_user_preferred_language_enum AS ENUM ('DUTCH', 'ENGLISH', 'DEUTSCH', 'FRENCH', 'SPANISH', 'ITALIAN', 'TURKISH');

CREATE TYPE shrine_identity_user_gender_enum AS ENUM ('MALE', 'FEMALE', 'UNKNOWN');

-- Organization domain
CREATE TYPE shrine_organization_role_enum AS ENUM ('GUEST', 'OWNER', 'ADMIN', 'MEMBER');

CREATE TYPE shrine_organization_member_status_enum AS ENUM ('ACTIVE', 'DISABLED');

CREATE TYPE shrine_organization_status_enum AS ENUM ('ACTIVE', 'DISABLED');

-- Notification domain
CREATE TYPE shrine_notification_email_template_type_enum AS ENUM (
            'WELCOME_EMAIL',
            'ORGANIZATION_INVITATION_EXISTING_USER',
            'ORGANIZATION_INVITATION_NEW_USER',
            'ORGANIZATION_INVITATION_ACCEPTED',
            'ORGANIZATION_MEMBER_REMOVED',
            'ORGANIZATION_ROLE_CHANGED',
            'PASSCHAT_SECRET_SHARED',
            'PASSCHAT_SECRET_EXPIRED',
            'PASSCHAT_STORAGE_ACK_REQUIRED',
            'UNUSUAL_ACTIVITY_DETECTED',
            'DATA_EXPORT_READY',
            'ACCOUNT_STATUS_CHANGED',
            'SYSTEM_NOTIFICATION'
        );
------Shrine-------

-- Kaizen domain
CREATE TYPE kaizen_board_member_role_enum AS ENUM ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER');

CREATE TYPE kaizen_sprint_status_enum AS ENUM ('PLANNED', 'ACTIVE', 'COMPLETED');

CREATE TYPE kaizen_ticket_type_enum AS ENUM ('ISSUE', 'FIX', 'HOTFIX', 'PROBLEM');

CREATE TYPE kaizen_ticket_status_enum AS ENUM ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE');

CREATE TYPE kaizen_ticket_priority_enum AS ENUM ('LOWEST', 'LOW', 'MEDIUM', 'HIGH', 'HIGHEST');

-- Lancer domain
CREATE TYPE lancer_image_pull_policy_enum AS ENUM ('ALWAYS', 'IF_NOT_PRESENT', 'NEVER');

CREATE TYPE lancer_container_status_enum AS ENUM ('CONTAINER_CREATED', 'CONTAINER_RUNNING', 'CONTAINER_EXITED', 'CONTAINER_UNKNOWN');

CREATE TYPE lancer_log_retention_day_enum AS ENUM ('SEVEN', 'FOURTEEN', 'THIRTY');

CREATE TYPE lancer_kubernetes_workload_type_enum AS ENUM ('DEPLOYMENT', 'STATEFULSET', 'DAEMONSET', 'JOB', 'CRONJOB');

CREATE TYPE lancer_log_stream_pod_status_enum AS ENUM ('RUNNING', 'TERMINATED', 'PENDING', 'UNKNOWN');

CREATE TYPE lancer_log_stream_pod_event_type_enum AS ENUM ('LOG', 'POD_CREATED', 'POD_DELETED', 'POD_RESTARTED', 'STREAM_STARTED', 'STREAM_STOPPED');

-- =====================================================
-- 3. Shrine domain
-- =====================================================
CREATE TABLE shrine.organizations (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NULL,
    industry VARCHAR(100) NULL,
    company_size VARCHAR(50) NULL,
    country VARCHAR(3) NULL,
    website VARCHAR(512) NULL,
    status shrine_organization_status_enum NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organizations_pk PRIMARY KEY (id)
);

CREATE TABLE shrine.users (
    id UUID NOT NULL,
    keycloak_id VARCHAR(255) NULL,
    email VARCHAR(254) NOT NULL,
    first_name VARCHAR(255) NULL,
    last_name VARCHAR(255) NULL,
    phone_number VARCHAR(30) NULL,
    user_position shrine_identity_user_position_enum NULL,
    user_preferred_language shrine_identity_user_preferred_language_enum DEFAULT 'ENGLISH'::shrine_identity_user_preferred_language_enum NULL,
    user_gender shrine_identity_user_gender_enum DEFAULT 'UNKNOWN'::shrine_identity_user_gender_enum NULL,
    status shrine_identity_user_status_enum NOT NULL DEFAULT 'PENDING_ONBOARDING'::shrine_identity_user_status_enum,
    default_organization_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_pk PRIMARY KEY (id),
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_default_organization_id_fk FOREIGN KEY (default_organization_id) REFERENCES shrine.organizations (id) ON DELETE SET NULL
);

CREATE TABLE shrine.users_roles (
    user_id UUID NOT NULL,
    role VARCHAR(255) NOT NULL,
    CONSTRAINT users_roles_pk PRIMARY KEY (user_id, role),
    CONSTRAINT users_roles_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE,
    CONSTRAINT users_roles_user_id_role_uk UNIQUE (user_id, role)
);

CREATE TABLE shrine.organization_members (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role shrine_organization_role_enum NOT NULL DEFAULT 'MEMBER'::shrine_organization_role_enum,
    status shrine_organization_member_status_enum NOT NULL DEFAULT 'ACTIVE'::shrine_organization_member_status_enum,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organization_members_pk PRIMARY KEY (id),
    CONSTRAINT organization_members_organization_id_user_id_uk UNIQUE (organization_id, user_id),
    CONSTRAINT organization_members_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT organization_members_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

CREATE TABLE shrine.user_invitations (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    role shrine_organization_role_enum NOT NULL,
    status shrine_identity_user_invitation_status_enum NOT NULL DEFAULT 'PENDING'::shrine_identity_user_invitation_status_enum,
    accepted_at TIMESTAMP WITH TIME ZONE NULL,
    invited_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_invitations_pk PRIMARY KEY (id),
    CONSTRAINT user_invitations_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT user_invitations_invited_by_user_id_fk FOREIGN KEY (invited_by_user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

CREATE TABLE shrine.users_codes (
    id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    type shrine_identity_user_code_type_enum NOT NULL,
    expired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invitation_role shrine_organization_role_enum NULL,
    invitation_email VARCHAR(254) NULL,
    user_id UUID NULL,
    organization_id UUID NULL,
    invited_by_user_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_codes_pk PRIMARY KEY (id),
    CONSTRAINT users_codes_token_hash_uk UNIQUE (token_hash),
    CONSTRAINT users_codes_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE,
    CONSTRAINT users_codes_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT users_codes_invited_by_user_id_fk FOREIGN KEY (invited_by_user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

CREATE TABLE shrine.notifications (
    id UUID NOT NULL,
    sender TEXT NOT NULL DEFAULT '',
    send BOOLEAN NOT NULL,
    email_template_type shrine_notification_email_template_type_enum NOT NULL,
    metadata JSONB NULL,
    recipients JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notifications_pk PRIMARY KEY (id)
);

-- =====================================================
-- 4. Kaizen domain
-- =====================================================
CREATE TABLE kaizen.boards (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(512) NULL,
    prefix VARCHAR(10) NOT NULL,
    color VARCHAR(7) NULL,
    icon VARCHAR(50) NULL,
    parent_board_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT boards_pk PRIMARY KEY (id),
    CONSTRAINT boards_parent_board_id_fk FOREIGN KEY (parent_board_id) REFERENCES kaizen.boards (id) ON DELETE SET NULL
);

CREATE TABLE kaizen.board_members (
    id UUID NOT NULL,
    role kaizen_board_member_role_enum NOT NULL,
    board_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT board_members_pk PRIMARY KEY (id),
    CONSTRAINT board_members_board_id_user_id_uk UNIQUE (board_id, user_id),
    CONSTRAINT board_members_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE,
    CONSTRAINT board_members_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id)
);

CREATE TABLE kaizen.sprints (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    goal VARCHAR(512) NULL,
    start_date TIMESTAMP WITH TIME ZONE NULL,
    end_date TIMESTAMP WITH TIME ZONE NULL,
    status kaizen_sprint_status_enum NOT NULL DEFAULT 'PLANNED'::kaizen_sprint_status_enum,
    number INTEGER NOT NULL,
    board_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sprints_pk PRIMARY KEY (id),
    CONSTRAINT sprints_board_id_number_uk UNIQUE (board_id, number),
    CONSTRAINT sprints_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE
);

CREATE TABLE kaizen.backlogs (
    id UUID NOT NULL,
    board_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT backlogs_pk PRIMARY KEY (id),
    CONSTRAINT backlogs_board_id_uk UNIQUE (board_id),
    CONSTRAINT backlogs_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE
);

CREATE TABLE kaizen.projects (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(7) NULL,
    board_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT projects_pk PRIMARY KEY (id),
    CONSTRAINT projects_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE
);

CREATE TABLE kaizen.labels (
    id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(7) NULL,
    board_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT labels_pk PRIMARY KEY (id),
    CONSTRAINT labels_board_id_name_uk UNIQUE (board_id, name),
    CONSTRAINT labels_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE
);

CREATE TABLE kaizen.tickets (
    id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT NULL,
    key VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    story_points INTEGER NULL,
    percentage INTEGER NULL,
    weight INTEGER NULL,
    mission_rank VARCHAR(10) NULL,
    type kaizen_ticket_type_enum NOT NULL,
    status kaizen_ticket_status_enum NOT NULL DEFAULT 'TODO'::kaizen_ticket_status_enum,
    priority kaizen_ticket_priority_enum NOT NULL DEFAULT 'MEDIUM'::kaizen_ticket_priority_enum,
    board_id UUID NOT NULL,
    sprint_id UUID NULL,
    backlog_id UUID NULL,
    parent_ticket_id UUID NULL,
    project_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tickets_pk PRIMARY KEY (id),
    CONSTRAINT tickets_key_uk UNIQUE (key),
    CONSTRAINT tickets_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE,
    CONSTRAINT tickets_sprint_id_fk FOREIGN KEY (sprint_id) REFERENCES kaizen.sprints (id) ON DELETE SET NULL,
    CONSTRAINT tickets_backlog_id_fk FOREIGN KEY (backlog_id) REFERENCES kaizen.backlogs (id) ON DELETE SET NULL,
    CONSTRAINT tickets_parent_ticket_id_fk FOREIGN KEY (parent_ticket_id) REFERENCES kaizen.tickets (id) ON DELETE SET NULL,
    CONSTRAINT tickets_project_id_fk FOREIGN KEY (project_id) REFERENCES kaizen.projects (id) ON DELETE SET NULL
);

CREATE TABLE kaizen.ticket_assignees (
    id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ticket_assignees_pk PRIMARY KEY (id),
    CONSTRAINT ticket_assignees_ticket_id_user_id_uk UNIQUE (ticket_id, user_id),
    CONSTRAINT ticket_assignees_ticket_id_fk FOREIGN KEY (ticket_id) REFERENCES kaizen.tickets (id) ON DELETE CASCADE,
    CONSTRAINT ticket_assignees_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id)
);

CREATE TABLE kaizen.ticket_reviewers (
    id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ticket_reviewers_pk PRIMARY KEY (id),
    CONSTRAINT ticket_reviewers_ticket_id_user_id_uk UNIQUE (ticket_id, user_id),
    CONSTRAINT ticket_reviewers_ticket_id_fk FOREIGN KEY (ticket_id) REFERENCES kaizen.tickets (id) ON DELETE CASCADE,
    CONSTRAINT ticket_reviewers_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id)
);

CREATE TABLE kaizen.comments (
    id UUID NOT NULL,
    content TEXT NOT NULL,
    ticket_id UUID NOT NULL,
    author_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT comments_pk PRIMARY KEY (id),
    CONSTRAINT comments_ticket_id_fk FOREIGN KEY (ticket_id) REFERENCES kaizen.tickets (id) ON DELETE CASCADE,
    CONSTRAINT comments_author_id_fk FOREIGN KEY (author_id) REFERENCES shrine.users (id)
);

CREATE TABLE kaizen.ticket_labels (
    ticket_id UUID NOT NULL,
    label_id UUID NOT NULL,
    CONSTRAINT ticket_labels_pk PRIMARY KEY (ticket_id, label_id),
    CONSTRAINT ticket_labels_ticket_id_fk FOREIGN KEY (ticket_id) REFERENCES kaizen.tickets (id) ON DELETE CASCADE,
    CONSTRAINT ticket_labels_label_id_fk FOREIGN KEY (label_id) REFERENCES kaizen.labels (id) ON DELETE CASCADE
);

-- =====================================================
-- 5. Lancer domain
-- =====================================================
CREATE TABLE lancer.nodes (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT nodes_pk PRIMARY KEY (id)
);

CREATE TABLE lancer.images (
    id VARCHAR(255) NOT NULL,
    tag VARCHAR(255) NOT NULL,
    size_mb BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT images_pk PRIMARY KEY (id)
);

CREATE TABLE lancer.pods (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    namespace VARCHAR(255) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    node_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pods_pk PRIMARY KEY (id),
    CONSTRAINT pods_node_id_fk FOREIGN KEY (node_id) REFERENCES lancer.nodes (id)
);

CREATE TABLE lancer.containers (
    id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    image_pull_policy lancer_image_pull_policy_enum NOT NULL,
    container_status lancer_container_status_enum NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    image_id VARCHAR(255) NOT NULL,
    pod_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT containers_pk PRIMARY KEY (id),
    CONSTRAINT containers_image_id_fk FOREIGN KEY (image_id) REFERENCES lancer.images (id),
    CONSTRAINT containers_pod_id_fk FOREIGN KEY (pod_id) REFERENCES lancer.pods (id)
);

CREATE TABLE lancer.image_names (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    image_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT image_names_pk PRIMARY KEY (id),
    CONSTRAINT image_names_image_id_fk FOREIGN KEY (image_id) REFERENCES lancer.images (id) ON DELETE CASCADE
);

CREATE TABLE lancer.nodes_images (
    node_id UUID NOT NULL,
    image_id VARCHAR(255) NOT NULL,
    CONSTRAINT nodes_images_pk PRIMARY KEY (node_id, image_id),
    CONSTRAINT nodes_images_node_id_fk FOREIGN KEY (node_id) REFERENCES lancer.nodes (id) ON DELETE CASCADE,
    CONSTRAINT nodes_images_image_id_fk FOREIGN KEY (image_id) REFERENCES lancer.images (id) ON DELETE CASCADE
);

CREATE TABLE lancer.log_streams (
    id UUID NOT NULL,
    log_stream_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    namespace VARCHAR(255) NULL,
    container_name VARCHAR(255) NULL,
    workload_name VARCHAR(255) NULL,
    workload_type lancer_kubernetes_workload_type_enum NULL,
    log_retention_day lancer_log_retention_day_enum NULL,
    is_retention_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_retention_date TIMESTAMP WITH TIME ZONE NULL,
    updated_retention_date TIMESTAMP WITH TIME ZONE NULL,
    retain_logs_till_date TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT log_streams_pk PRIMARY KEY (id)
);

CREATE TABLE lancer.log_stream_match_labels (
    log_stream_id UUID NOT NULL,
    label_key     VARCHAR(255) NOT NULL,
    label_value   VARCHAR(255) NOT NULL,
    CONSTRAINT log_stream_match_labels_pk PRIMARY KEY (log_stream_id, label_key),
    CONSTRAINT log_stream_match_labels_log_stream_id_fk FOREIGN KEY (log_stream_id) REFERENCES lancer.log_streams (id) ON DELETE CASCADE
);

CREATE TABLE lancer.log_stream_pods (
    id            UUID NOT NULL,
    pod_name      VARCHAR(255) NOT NULL,
    pod_uid       VARCHAR(255) NULL,
    restart_count INTEGER NOT NULL DEFAULT 0,
    died_at       TIMESTAMP WITH TIME ZONE NULL,
    status        lancer_log_stream_pod_status_enum NOT NULL DEFAULT 'UNKNOWN',
    log_stream_id UUID NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT log_stream_pods_pk PRIMARY KEY (id),
    CONSTRAINT log_stream_pods_log_stream_id_fk FOREIGN KEY (log_stream_id) REFERENCES lancer.log_streams (id) ON DELETE CASCADE
);

CREATE TABLE lancer.log_stream_pod_events (
    id                UUID NOT NULL,
    event_type        lancer_log_stream_pod_event_type_enum NOT NULL,
    content           TEXT NULL,
    event_timestamp   TIMESTAMP WITH TIME ZONE NOT NULL,
    log_stream_pod_id UUID NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT log_stream_pod_events_pk PRIMARY KEY (id),
    CONSTRAINT log_stream_pod_events_log_stream_pod_id_fk FOREIGN KEY (log_stream_pod_id) REFERENCES lancer.log_stream_pods (id) ON DELETE CASCADE
);

-- =====================================================
-- 6. Indexes
-- =====================================================
CREATE INDEX users_codes_invitation_email_idx ON shrine.users_codes (invitation_email);

CREATE INDEX boards_parent_board_id_idx ON kaizen.boards (parent_board_id);

CREATE INDEX board_members_user_id_idx ON kaizen.board_members (user_id);

CREATE INDEX board_members_board_id_idx ON kaizen.board_members (board_id);

CREATE INDEX sprints_board_id_idx ON kaizen.sprints (board_id);

CREATE INDEX sprints_status_idx ON kaizen.sprints (status);

CREATE INDEX projects_board_id_idx ON kaizen.projects (board_id);

CREATE INDEX labels_board_id_idx ON kaizen.labels (board_id);

CREATE INDEX tickets_board_id_idx ON kaizen.tickets (board_id);

CREATE INDEX tickets_sprint_id_idx ON kaizen.tickets (sprint_id);

CREATE INDEX tickets_backlog_id_idx ON kaizen.tickets (backlog_id);

CREATE INDEX tickets_status_idx ON kaizen.tickets (status);

CREATE INDEX tickets_type_idx ON kaizen.tickets (type);

CREATE INDEX tickets_priority_idx ON kaizen.tickets (priority);

CREATE INDEX tickets_parent_ticket_id_idx ON kaizen.tickets (parent_ticket_id);

CREATE INDEX tickets_project_id_idx ON kaizen.tickets (project_id);

CREATE INDEX comments_ticket_id_idx ON kaizen.comments (ticket_id);

CREATE INDEX comments_author_id_idx ON kaizen.comments (author_id);

CREATE INDEX ticket_assignees_ticket_id_idx ON kaizen.ticket_assignees (ticket_id);

CREATE INDEX ticket_assignees_user_id_idx ON kaizen.ticket_assignees (user_id);

CREATE INDEX ticket_reviewers_ticket_id_idx ON kaizen.ticket_reviewers (ticket_id);

CREATE INDEX ticket_reviewers_user_id_idx ON kaizen.ticket_reviewers (user_id);

CREATE INDEX ticket_labels_ticket_id_idx ON kaizen.ticket_labels (ticket_id);

CREATE INDEX ticket_labels_label_id_idx ON kaizen.ticket_labels (label_id);

CREATE INDEX log_stream_pods_log_stream_id_idx ON lancer.log_stream_pods (log_stream_id);

CREATE INDEX log_stream_pod_events_log_stream_pod_id_idx ON lancer.log_stream_pod_events (log_stream_pod_id);

CREATE INDEX log_stream_pod_events_event_type_event_timestamp_idx ON lancer.log_stream_pod_events (event_type, event_timestamp);

CREATE INDEX log_stream_pod_events_log_stream_pod_id_event_timestamp_idx ON lancer.log_stream_pod_events (log_stream_pod_id, event_timestamp);

CREATE INDEX log_stream_pod_events_event_timestamp_idx ON lancer.log_stream_pod_events (event_timestamp);

-- User Invitations (simplified flow: no token/expiry needed)
CREATE INDEX user_invitations_email_status_idx ON shrine.user_invitations(email, status);
CREATE INDEX user_invitations_organization_id_status_idx ON shrine.user_invitations(organization_id, status);
