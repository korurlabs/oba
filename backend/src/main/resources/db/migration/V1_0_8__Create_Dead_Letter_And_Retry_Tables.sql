-- Village Database Schema - Create Dead Letter And Retry Tables
-- V1_0_8__Create_Dead_Letter_And_Retry_Tables.sql
--
-- Creates dead letter and retry tables for the outbox and retry domains.
-- Includes: dead letter resolution enum, retry event status enum,
-- connect retry system enum, outbox_dead_events, retry_events, retry_dead_events.

-- =====================================================
-- 1. Enum types
-- =====================================================

-- Dead letter resolution enum (shared by outbox and retry dead letter tables)
CREATE TYPE shrine_dead_letter_resolution_enum AS ENUM ('REPLAYED', 'DISCARDED');

-- Retry event status enum
CREATE TYPE shrine_retry_event_status_enum AS ENUM ('COMPLETED', 'EXHAUSTED');

-- Retry system enum (mirrors ConnectRetrySystem Java enum)
CREATE TYPE shrine_connect_retry_system_enum AS ENUM (
    'AZURE_BLOB_STORAGE', 'MOLLIE', 'INTELLIGENCE', 'KUBERNETES', 'NOTIFICATION'
);

-- =====================================================
-- 2. Tables
-- =====================================================

-- Outbox Dead Letter

CREATE TABLE shrine.outbox_dead_events (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    original_event_id      UUID         NOT NULL,
    payload                TEXT         NOT NULL,
    event_type             shrine_outbox_event_type_enum     NOT NULL,
    target_system          shrine_outbox_target_system_enum  NOT NULL,
    attempts               INTEGER      NOT NULL,
    last_error_message     TEXT,
    last_error_values      TEXT,
    last_error_stack_trace TEXT,
    failed_at              TIMESTAMPTZ  NOT NULL,
    resolution             shrine_dead_letter_resolution_enum,
    resolved_at            TIMESTAMPTZ,
    original_created_at    TIMESTAMPTZ  NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT outbox_dead_events_pk PRIMARY KEY (id)
);

-- Retry Events

CREATE TABLE shrine.retry_events (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    method_name            VARCHAR(255) NOT NULL,
    system                 shrine_connect_retry_system_enum  NOT NULL,
    attempts               INTEGER      NOT NULL,
    max_attempts           INTEGER      NOT NULL,
    status                 shrine_retry_event_status_enum    NOT NULL,
    last_error_message     TEXT,
    last_error_stack_trace TEXT,
    duration_ms            BIGINT       NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT retry_events_pk PRIMARY KEY (id)
);

-- Retry Dead Letter

CREATE TABLE shrine.retry_dead_events (
    id                        UUID         NOT NULL DEFAULT gen_random_uuid(),
    original_retry_event_id   UUID         NOT NULL,
    method_name               VARCHAR(255) NOT NULL,
    system                    shrine_connect_retry_system_enum  NOT NULL,
    attempts                  INTEGER      NOT NULL,
    last_error_message        TEXT,
    last_error_stack_trace    TEXT,
    failed_at                 TIMESTAMPTZ  NOT NULL,
    resolution                shrine_dead_letter_resolution_enum,
    resolved_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT retry_dead_events_pk PRIMARY KEY (id)
);

-- =====================================================
-- 3. Indexes
-- =====================================================

CREATE INDEX outbox_dead_events_failed_at_idx
    ON shrine.outbox_dead_events (failed_at)
    WHERE resolution IS NULL;

CREATE INDEX retry_events_status_created_at_idx
    ON shrine.retry_events (status, created_at);

CREATE INDEX retry_dead_events_failed_at_idx
    ON shrine.retry_dead_events (failed_at)
    WHERE resolution IS NULL;
