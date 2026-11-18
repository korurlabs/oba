-- Village Database Schema - Create Outbox And Storage Tables
-- V1_0_6__Create_Outbox_And_Storage_Tables.sql
--
-- Outbox domain: transactional outbox for reliable event delivery.
-- Storage domain: file storage with attachment and photo sub-domains.
-- All enum values (including connect layer integrations: Azure Blob,
-- Kubernetes, Mollie, Intelligence) are included in the initial CREATE TYPE
-- definitions so no ALTER TYPE ADD VALUE is needed separately.
--
-- Consolidates: V1_0_8 (Create_Outbox_And_Storage_Tables),
--               V1_1_1 (Add_Outbox_Connect_Enum_Values) — original numbering

-- =====================================================
-- 1. Enum types
-- =====================================================

CREATE TYPE shrine_outbox_status_enum AS ENUM ('ENQUEUED', 'PROCESSING', 'PROCESSED', 'FAILED');

CREATE TYPE shrine_outbox_event_type_enum AS ENUM (
    'INVITATION_EXISTING_USER',
    'INVITATION_NEW_USER',
    'ORGANIZATION_MEMBER_REMOVED',
    'ORGANIZATION_ROLE_CHANGED',
    'PASSCHAT_SECRET_SHARED',
    'PASSCHAT_SECRET_EXPIRED',
    'PASSCHAT_STORAGE_ACK_REQUIRED',
    'UNUSUAL_ACTIVITY_DETECTED',
    'WELCOME_EMAIL',
    'ACCOUNT_STATUS_CHANGED',
    'DATA_EXPORT_READY',
    'SYSTEM_NOTIFICATION',
    'AZURE_BLOB_UPLOAD',
    'AZURE_BLOB_DELETE',
    'KUBERNETES_COMMAND_EXECUTION',
    'KUBERNETES_DEPLOYMENT_TRIGGER',
    'MOLLIE_CUSTOMER_CREATE',
    'MOLLIE_PAYMENT_CREATE',
    'MOLLIE_SUBSCRIPTION_CREATE',
    'MOLLIE_SUBSCRIPTION_CANCEL',
    'INTELLIGENCE_CHAT_REQUEST',
    'INTELLIGENCE_MULTIMODAL_REQUEST'
);

CREATE TYPE shrine_outbox_target_system_enum AS ENUM (
    'NOTIFICATION',
    'AZURE_BLOB_STORAGE',
    'KUBERNETES',
    'MOLLIE',
    'INTELLIGENCE'
);

CREATE TYPE shrine_storage_entity_type_enum AS ENUM ('ORGANIZATION', 'USER');

-- =====================================================
-- 2. Tables
-- =====================================================

CREATE TABLE shrine.outbox_events (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    payload               TEXT         NOT NULL,
    event_type            shrine_outbox_event_type_enum     NOT NULL,
    status                shrine_outbox_status_enum         NOT NULL DEFAULT 'ENQUEUED',
    target_system         shrine_outbox_target_system_enum  NOT NULL,
    attempts              INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at       TIMESTAMPTZ,
    processed_at          TIMESTAMPTZ,
    last_error_message    TEXT,
    last_error_values     TEXT,
    last_error_stack_trace TEXT,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT outbox_events_pk PRIMARY KEY (id)
);

-- Attachment sub-domain: generic file attachments (any format)
CREATE TABLE shrine.attachments (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    url           TEXT         NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100),
    file_size     BIGINT,
    entity_type   shrine_storage_entity_type_enum NOT NULL,
    entity_id     UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT attachments_pk PRIMARY KEY (id)
);

-- Photo sub-domain: image files with display metadata (webp only)
CREATE TABLE shrine.photos (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    url           TEXT         NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    alt           VARCHAR(500),
    sort_index    INTEGER      NOT NULL DEFAULT 0,
    is_main       BOOLEAN      NOT NULL DEFAULT FALSE,
    entity_type   shrine_storage_entity_type_enum NOT NULL,
    entity_id     UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT photos_pk PRIMARY KEY (id)
);

-- =====================================================
-- 3. Indexes
-- =====================================================

-- Index for scheduler polling: pick ENQUEUED events ready for processing
CREATE INDEX outbox_events_status_next_attempt_at_created_at_idx
    ON shrine.outbox_events (status, next_attempt_at, created_at)
    WHERE status = 'ENQUEUED';

CREATE INDEX attachments_entity_type_entity_id_idx
    ON shrine.attachments (entity_type, entity_id);

CREATE INDEX photos_entity_type_entity_id_idx
    ON shrine.photos (entity_type, entity_id);
