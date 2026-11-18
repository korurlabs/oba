-- Oba Database Schema - Otak: Documents And Outreach Reach-Outs
-- V1_2_119__Otak_Documents_And_Outreach_Reach_Outs.sql
--
-- Splits the outreach of V1_2_110 (a reusable mail of an organization) into three
-- things. A document template is reusable content of an organization. A
-- document is a copy of content made for one relation. An outreach is one
-- reach-out to one relation and one contact: it holds no content, only links to
-- the documents that were sent or are about to be sent.
--
-- The outreaches of V1_2_110 survive as MAIL document templates in Dutch (NL) with the
-- same id, name, subject, body, organization and timestamps. Then the old
-- outreaches table is dropped and created again in its new shape, because its
-- rows have no relation and cannot be reach-outs.
--
-- The enum types are created in the public schema with an otak_ prefix, as in
-- V1_2_110. otak_outreach_type_enum (MAIL) already exists and is reused.
--
-- Delete rules:
--   document_templates_organization_id_fk ON DELETE CASCADE: deleting an
--     organization deletes its templates.
--   documents_relation_id_organization_id_fk ON DELETE CASCADE: deleting a
--     relation deletes its documents (and, in the same statement, its
--     outreaches and attachments).
--   documents_template_id_fk ON DELETE SET NULL: a document outlives the
--     template it was copied from, the link is provenance only.
--   outreaches_relation_id_organization_id_fk ON DELETE CASCADE: deleting a
--     relation deletes its outreaches.
--   outreaches_contact_id_fk ON DELETE SET NULL: the outreach record outlives
--     a removed contact; sent_to keeps the address it went to.
--   outreaches_mail_document_id_relation_id_fk NO ACTION: a mail document that
--     an outreach uses cannot be deleted on its own. A relation delete still
--     works because it removes the outreach in the same statement. As the key
--     includes relation_id, the database itself keeps the mail on the
--     outreach's relation.
--   outreaches_mail_sender_id_fk ON DELETE SET NULL: the outreach outlives a
--     removed mail sender.
--   outreach_attachments_outreach_id_relation_id_fk ON DELETE CASCADE: deleting
--     an outreach deletes its attachment links, not the documents.
--   outreach_attachments_document_id_relation_id_fk ON DELETE CASCADE: deleting
--     a document removes it from every outreach it was attached to; the
--     service refuses this for a locked document before it gets here.
--
-- Indexes:
--   document_templates_organization_id_lower_name_language_uk is unique per
--     organization on lower-case name and language, which enforces the name
--     rule, serves the list order and search, and serves the organization
--     foreign key and its cascade.
--   documents_relation_id_organization_id_idx serves the documents of a
--     relation and the cascade of the composite relation foreign key.
--   documents_template_id_idx serves the template foreign key and its SET NULL.
--   outreaches_relation_id_organization_id_idx serves the outreaches of a
--     relation and the cascade of the composite relation foreign key.
--   outreaches_contact_id_idx serves the contact foreign key and its SET NULL.
--   outreaches_mail_sender_id_idx serves the mail sender foreign key and its
--     SET NULL.
--   outreaches_organization_id_status_idx serves the list filtered by status.
--   outreaches_mail_document_id_uk is unique: a mail document belongs to at
--     most one outreach. It also serves the mail document foreign key.
--   outreach_attachments_pk (outreach_id, document_id) serves the attachments
--     of an outreach and forbids attaching a document twice.
--   outreach_attachments_document_id_idx serves the lock check (is this
--     document attached anywhere) and the document foreign key cascade.
--
-- Unique constraints that exist only as targets of composite foreign keys:
--   documents_id_relation_id_uk and outreaches_id_relation_id_uk.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE otak_document_type_enum AS ENUM ('MAIL', 'PROPOSAL', 'NDA', 'TERMS', 'REPORT', 'SERVICE_SHEET', 'PRESENTATION', 'OTHER');
CREATE TYPE otak_document_language_enum AS ENUM ('NL', 'TR', 'EN');
CREATE TYPE otak_outreach_status_enum AS ENUM ('DRAFT', 'SENT', 'FAILED');

-- =====================================================
-- 2. Create document templates and copy the V1_2_110 outreaches into them
-- =====================================================

CREATE TABLE otak.document_templates (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    type otak_document_type_enum NOT NULL,
    language otak_document_language_enum NOT NULL,
    subject VARCHAR(255),
    body TEXT,
    html_blob_url VARCHAR(500),
    pdf_blob_url VARCHAR(500),
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT document_templates_pk PRIMARY KEY (id),
    CONSTRAINT document_templates_content_by_type_ck CHECK (
        (type = 'MAIL'::otak_document_type_enum
            AND subject IS NOT NULL AND body IS NOT NULL
            AND html_blob_url IS NULL AND pdf_blob_url IS NULL)
        OR (type <> 'MAIL'::otak_document_type_enum
            AND subject IS NULL AND body IS NULL)),
    CONSTRAINT document_templates_body_ck CHECK ((char_length(body) <= 10000)),
    CONSTRAINT document_templates_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX document_templates_organization_id_lower_name_language_uk ON otak.document_templates USING btree (organization_id, lower((name)::text), language);

INSERT INTO otak.document_templates (id, name, type, language, subject, body, organization_id, created_at, updated_at)
SELECT id, name, 'MAIL'::otak_document_type_enum, 'NL'::otak_document_language_enum, subject, body, organization_id, created_at, updated_at
FROM otak.outreaches;

-- =====================================================
-- 3. Recreate outreaches in its new shape
-- =====================================================

DROP TABLE otak.outreaches;

CREATE TABLE otak.documents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    type otak_document_type_enum NOT NULL,
    language otak_document_language_enum NOT NULL,
    subject VARCHAR(255),
    body TEXT,
    html_blob_url VARCHAR(500),
    pdf_blob_url VARCHAR(500),
    template_id UUID,
    organization_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT documents_pk PRIMARY KEY (id),
    CONSTRAINT documents_id_relation_id_uk UNIQUE (id, relation_id),
    CONSTRAINT documents_content_by_type_ck CHECK (
        (type = 'MAIL'::otak_document_type_enum
            AND subject IS NOT NULL AND body IS NOT NULL
            AND html_blob_url IS NULL AND pdf_blob_url IS NULL)
        OR (type <> 'MAIL'::otak_document_type_enum
            AND subject IS NULL AND body IS NULL)),
    CONSTRAINT documents_body_ck CHECK ((char_length(body) <= 10000)),
    CONSTRAINT documents_relation_id_organization_id_fk FOREIGN KEY (relation_id, organization_id) REFERENCES otak.relations(id, organization_id) ON DELETE CASCADE,
    CONSTRAINT documents_template_id_fk FOREIGN KEY (template_id) REFERENCES otak.document_templates(id) ON DELETE SET NULL
);

CREATE INDEX documents_relation_id_organization_id_idx ON otak.documents USING btree (relation_id, organization_id);
CREATE INDEX documents_template_id_idx ON otak.documents USING btree (template_id);

CREATE TABLE otak.outreaches (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    type otak_outreach_type_enum NOT NULL,
    status otak_outreach_status_enum NOT NULL DEFAULT 'DRAFT',
    sent_to VARCHAR(254),
    sent_at TIMESTAMP WITH TIME ZONE,
    organization_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    contact_id UUID,
    mail_document_id UUID NOT NULL,
    mail_sender_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT outreaches_pk PRIMARY KEY (id),
    CONSTRAINT outreaches_id_relation_id_uk UNIQUE (id, relation_id),
    CONSTRAINT outreaches_mail_document_id_uk UNIQUE (mail_document_id),
    CONSTRAINT outreaches_sent_ck CHECK ((status <> 'SENT'::otak_outreach_status_enum) OR (sent_at IS NOT NULL AND sent_to IS NOT NULL)),
    CONSTRAINT outreaches_relation_id_organization_id_fk FOREIGN KEY (relation_id, organization_id) REFERENCES otak.relations(id, organization_id) ON DELETE CASCADE,
    CONSTRAINT outreaches_contact_id_fk FOREIGN KEY (contact_id) REFERENCES otak.contacts(id) ON DELETE SET NULL,
    CONSTRAINT outreaches_mail_document_id_relation_id_fk FOREIGN KEY (mail_document_id, relation_id) REFERENCES otak.documents(id, relation_id),
    CONSTRAINT outreaches_mail_sender_id_fk FOREIGN KEY (mail_sender_id) REFERENCES otak.mail_senders(id) ON DELETE SET NULL
);

CREATE INDEX outreaches_relation_id_organization_id_idx ON otak.outreaches USING btree (relation_id, organization_id);
CREATE INDEX outreaches_contact_id_idx ON otak.outreaches USING btree (contact_id);
CREATE INDEX outreaches_mail_sender_id_idx ON otak.outreaches USING btree (mail_sender_id);
CREATE INDEX outreaches_organization_id_status_idx ON otak.outreaches USING btree (organization_id, status);

CREATE TABLE otak.outreach_attachments (
    outreach_id UUID NOT NULL,
    document_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    CONSTRAINT outreach_attachments_pk PRIMARY KEY (outreach_id, document_id),
    CONSTRAINT outreach_attachments_outreach_id_relation_id_fk FOREIGN KEY (outreach_id, relation_id) REFERENCES otak.outreaches(id, relation_id) ON DELETE CASCADE,
    CONSTRAINT outreach_attachments_document_id_relation_id_fk FOREIGN KEY (document_id, relation_id) REFERENCES otak.documents(id, relation_id) ON DELETE CASCADE
);

CREATE INDEX outreach_attachments_document_id_idx ON otak.outreach_attachments USING btree (document_id);
