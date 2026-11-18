-- Village Database Schema - Iroh Sales Messages
-- V1_2_46__Iroh_Sales_Messages.sql
--
-- Creates iroh.sales_messages, which records every outreach message sent (or
-- pending send) to a prospect. Messages are AI-drafted, human-approved, and
-- tracked through their full lifecycle from DRAFT to RESPONDED or NO_RESPONSE.
--
-- Design decisions:
--   - template_id is SET NULL on delete: deleting a template does not destroy the
--     sent-message audit trail. The service layer prevents deletion of templates
--     that still have SENT messages.
--   - approved_by_id and rejected_by_id are nullable FKs to shrine.users; they
--     are SET NULL so that user deactivation does not orphan message records.
--   - sent_via VARCHAR(20) defaults to 'EMAIL'; kept as VARCHAR rather than an
--     enum to allow future channel additions without a migration.
--   - updated_at is nullable (NULL until first update) — matches BaseEntity pattern.

-- =====================================================
-- 1. Create table
-- =====================================================

CREATE TABLE iroh.sales_messages (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    relation_id          UUID         NOT NULL,
    template_id          UUID,
    organization_id      UUID         NOT NULL,
    subject              VARCHAR(500) NOT NULL,
    body                 TEXT         NOT NULL,
    status               iroh_sales_message_status_enum NOT NULL DEFAULT 'DRAFT',
    sent_via             VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    sent_at              TIMESTAMP WITH TIME ZONE,
    responded_at         TIMESTAMP WITH TIME ZONE,
    responded_content    TEXT,
    no_response_flag_at  TIMESTAMP WITH TIME ZONE,
    approved_by_id       UUID,
    approved_at          TIMESTAMP WITH TIME ZONE,
    rejected_by_id       UUID,
    rejected_at          TIMESTAMP WITH TIME ZONE,
    reject_reason        VARCHAR(500),
    deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE,
    CONSTRAINT sales_messages_pk
        PRIMARY KEY (id),
    CONSTRAINT sales_messages_relation_id_fk
        FOREIGN KEY (relation_id) REFERENCES iroh.relations(id) ON DELETE CASCADE,
    CONSTRAINT sales_messages_template_id_fk
        FOREIGN KEY (template_id) REFERENCES iroh.sales_message_templates(id) ON DELETE SET NULL,
    CONSTRAINT sales_messages_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT sales_messages_approved_by_id_fk
        FOREIGN KEY (approved_by_id) REFERENCES shrine.users(id) ON DELETE SET NULL,
    CONSTRAINT sales_messages_rejected_by_id_fk
        FOREIGN KEY (rejected_by_id) REFERENCES shrine.users(id) ON DELETE SET NULL
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Primary query path: all messages for an organisation filtered by relation.
CREATE INDEX sales_messages_organization_id_relation_id_idx
    ON iroh.sales_messages(organization_id, relation_id);

-- Status-filtered views (DRAFT queue, SENT list, etc.) on non-deleted rows only.
CREATE INDEX sales_messages_status_idx
    ON iroh.sales_messages(status)
    WHERE deleted = FALSE;

-- FK indexes required by convention.
CREATE INDEX sales_messages_relation_id_idx
    ON iroh.sales_messages(relation_id);

CREATE INDEX sales_messages_template_id_idx
    ON iroh.sales_messages(template_id);

CREATE INDEX sales_messages_approved_by_id_idx
    ON iroh.sales_messages(approved_by_id);

CREATE INDEX sales_messages_rejected_by_id_idx
    ON iroh.sales_messages(rejected_by_id);
