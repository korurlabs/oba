-- Oba Database Schema - Otak: Split Mail Templates And Mails From Documents
-- V1_2_122__Otak_Split_Mail_Templates_And_Mails.sql
--
-- A mail is not a document: it has text content and no files. V1_2_119 stored
-- mails as MAIL rows of document_templates and documents, which forced every
-- document column and check to branch on the type. This migration moves them
-- out into their own tables:
--   mail_templates  reusable mail content of an organization (was a MAIL row of
--                   document_templates)
--   mails           the mail of one outreach, written for one relation (was a
--                   MAIL row of documents)
--   mail_attachments the documents attached to a mail (was outreach_attachments)
-- and the outreach points at its mail with outreaches.mail_id instead of
-- outreaches.mail_document_id.
--
-- Data: every MAIL document template becomes a mail template and every MAIL
-- document that an outreach uses becomes a mail, each with the same id, content
-- and timestamps. mails.mail_template_id keeps the provenance link when the
-- template it was copied from was a MAIL template, otherwise it is null. The
-- attachment links follow the outreach to its mail. MAIL documents that no
-- outreach uses are deleted (nothing reads them), then the MAIL document
-- templates, which now live in mail_templates.
--
-- document_templates and documents lose subject and body and the checks that
-- tied content to the type. Their type enum loses MAIL.
--
-- Delete rules:
--   mail_templates_organization_id_fk ON DELETE CASCADE: deleting an
--     organization deletes its mail templates.
--   mails_relation_id_organization_id_fk ON DELETE CASCADE: deleting a relation
--     deletes its mails (and, in the same statement, its outreaches and mail
--     attachments).
--   mails_mail_template_id_fk ON DELETE SET NULL: a mail outlives the template
--     it was copied from, the link is provenance only.
--   outreaches_mail_id_relation_id_fk NO ACTION: a mail that an outreach uses
--     cannot be deleted on its own. A relation delete still works because it
--     removes the outreach in the same statement. As the key includes
--     relation_id, the database itself keeps the mail on the outreach's
--     relation.
--   mail_attachments_mail_id_relation_id_fk ON DELETE CASCADE: deleting a mail
--     deletes its attachment links, not the documents.
--   mail_attachments_document_id_relation_id_fk ON DELETE CASCADE: deleting a
--     document removes it from every mail it was attached to; the service
--     refuses this for a locked document before it gets here.
--
-- Indexes:
--   mail_templates_organization_id_lower_name_language_uk is unique per
--     organization on lower-case name and language, which enforces the name
--     rule, serves the list order and search, and serves the organization
--     foreign key and its cascade.
--   mails_relation_id_organization_id_idx serves the composite relation foreign
--     key and its cascade.
--   mails_mail_template_id_idx serves the template foreign key and its SET
--     NULL.
--   outreaches_mail_id_uk is unique: a mail belongs to at most one outreach. It
--     also serves the mail foreign key.
--   mail_attachments_pk (mail_id, document_id) serves the attachments of a mail
--     and forbids attaching a document twice.
--   mail_attachments_document_id_idx serves the lock check (is this document
--     attached anywhere) and the document foreign key cascade.
--
-- Unique constraints that exist only as targets of composite foreign keys:
--   mails_id_relation_id_uk.

-- =====================================================
-- 1. Create mail templates and copy the MAIL document templates into them
-- =====================================================

CREATE TABLE otak.mail_templates (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    language otak_document_language_enum NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mail_templates_pk PRIMARY KEY (id),
    CONSTRAINT mail_templates_body_ck CHECK ((char_length(body) <= 10000)),
    CONSTRAINT mail_templates_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX mail_templates_organization_id_lower_name_language_uk ON otak.mail_templates USING btree (organization_id, lower((name)::text), language);

INSERT INTO otak.mail_templates (id, name, language, subject, body, organization_id, created_at, updated_at)
SELECT id, name, language, subject, body, organization_id, created_at, updated_at
FROM otak.document_templates
WHERE type = 'MAIL'::otak_document_type_enum;

-- =====================================================
-- 2. Create mails and copy the MAIL documents that an outreach uses
-- =====================================================

CREATE TABLE otak.mails (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    language otak_document_language_enum NOT NULL,
    organization_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    mail_template_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mails_pk PRIMARY KEY (id),
    CONSTRAINT mails_id_relation_id_uk UNIQUE (id, relation_id),
    CONSTRAINT mails_body_ck CHECK ((char_length(body) <= 10000)),
    CONSTRAINT mails_relation_id_organization_id_fk FOREIGN KEY (relation_id, organization_id) REFERENCES otak.relations(id, organization_id) ON DELETE CASCADE,
    CONSTRAINT mails_mail_template_id_fk FOREIGN KEY (mail_template_id) REFERENCES otak.mail_templates(id) ON DELETE SET NULL
);

CREATE INDEX mails_relation_id_organization_id_idx ON otak.mails USING btree (relation_id, organization_id);
CREATE INDEX mails_mail_template_id_idx ON otak.mails USING btree (mail_template_id);

INSERT INTO otak.mails (id, subject, body, language, organization_id, relation_id, mail_template_id, created_at, updated_at)
SELECT d.id, d.subject, d.body, d.language, d.organization_id, d.relation_id, mt.id, d.created_at, d.updated_at
FROM otak.documents d
LEFT JOIN otak.mail_templates mt ON mt.id = d.template_id
WHERE d.type = 'MAIL'::otak_document_type_enum
  AND d.id IN (SELECT o.mail_document_id FROM otak.outreaches o);

-- =====================================================
-- 3. Point outreaches at their mail
-- =====================================================

ALTER TABLE otak.outreaches ADD COLUMN mail_id UUID;

UPDATE otak.outreaches SET mail_id = mail_document_id;

ALTER TABLE otak.outreaches ALTER COLUMN mail_id SET NOT NULL;

ALTER TABLE otak.outreaches ADD CONSTRAINT outreaches_mail_id_uk UNIQUE (mail_id);
ALTER TABLE otak.outreaches ADD CONSTRAINT outreaches_mail_id_relation_id_fk
    FOREIGN KEY (mail_id, relation_id) REFERENCES otak.mails(id, relation_id);

ALTER TABLE otak.outreaches DROP CONSTRAINT outreaches_mail_document_id_relation_id_fk;
ALTER TABLE otak.outreaches DROP CONSTRAINT outreaches_mail_document_id_uk;
ALTER TABLE otak.outreaches DROP COLUMN mail_document_id;

-- =====================================================
-- 4. Create mail attachments, copy the outreach attachments, drop them
-- =====================================================

CREATE TABLE otak.mail_attachments (
    mail_id UUID NOT NULL,
    document_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    CONSTRAINT mail_attachments_pk PRIMARY KEY (mail_id, document_id),
    CONSTRAINT mail_attachments_mail_id_relation_id_fk FOREIGN KEY (mail_id, relation_id) REFERENCES otak.mails(id, relation_id) ON DELETE CASCADE,
    CONSTRAINT mail_attachments_document_id_relation_id_fk FOREIGN KEY (document_id, relation_id) REFERENCES otak.documents(id, relation_id) ON DELETE CASCADE
);

CREATE INDEX mail_attachments_document_id_idx ON otak.mail_attachments USING btree (document_id);

INSERT INTO otak.mail_attachments (mail_id, document_id, relation_id)
SELECT o.mail_id, oa.document_id, oa.relation_id
FROM otak.outreach_attachments oa
JOIN otak.outreaches o ON o.id = oa.outreach_id AND o.relation_id = oa.relation_id;

DROP TABLE otak.outreach_attachments;

-- =====================================================
-- 5. Remove MAIL rows and the content columns from documents and templates
-- =====================================================

DELETE FROM otak.documents WHERE type = 'MAIL'::otak_document_type_enum;
DELETE FROM otak.document_templates WHERE type = 'MAIL'::otak_document_type_enum;

ALTER TABLE otak.document_templates DROP CONSTRAINT document_templates_content_by_type_ck;
ALTER TABLE otak.document_templates DROP CONSTRAINT document_templates_body_ck;
ALTER TABLE otak.document_templates DROP COLUMN subject;
ALTER TABLE otak.document_templates DROP COLUMN body;

ALTER TABLE otak.documents DROP CONSTRAINT documents_content_by_type_ck;
ALTER TABLE otak.documents DROP CONSTRAINT documents_body_ck;
ALTER TABLE otak.documents DROP COLUMN subject;
ALTER TABLE otak.documents DROP COLUMN body;

-- =====================================================
-- 6. Remove MAIL from the document type enum
-- =====================================================

ALTER TYPE otak_document_type_enum RENAME TO otak_document_type_enum_old;
CREATE TYPE otak_document_type_enum AS ENUM ('PROPOSAL', 'NDA', 'TERMS', 'REPORT', 'SERVICE_SHEET', 'PRESENTATION', 'OTHER');
ALTER TABLE otak.document_templates
    ALTER COLUMN type TYPE otak_document_type_enum
    USING type::text::otak_document_type_enum;
ALTER TABLE otak.documents
    ALTER COLUMN type TYPE otak_document_type_enum
    USING type::text::otak_document_type_enum;
DROP TYPE otak_document_type_enum_old;
