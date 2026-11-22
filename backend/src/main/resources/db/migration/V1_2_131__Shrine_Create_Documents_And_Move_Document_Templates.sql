-- Oba Database Schema - Shrine: Create Documents And Move Document Templates
-- V1_2_131__Shrine_Create_Documents_And_Move_Document_Templates.sql
--
-- Documents are an organization-wide shrine domain. A document belongs to one
-- legal entity of an organization and to one owner (today a relation). This
-- migration:
--   1. creates shrine_document_entity_type_enum (RELATION), the owner types of
--      a document, as shrine_storage_entity_type_enum is for attachments;
--   2. moves otak.document_templates to shrine.document_templates with the same
--      ids and values, and adds legal_entity_id. Every existing template gets
--      NULL there: it stays usable by every legal entity of its organization,
--      so no legal entity has to be chosen for an organization that has none.
--      It also adds created_by_id and updated_by_id, NOT NULL. Existing
--      templates have no author, so both are filled with the user of the first
--      OWNER membership of the organization (earliest created_at, ties by
--      membership id). An organization with templates and no OWNER aborts the
--      migration with an exception that names it;
--   3. creates shrine.documents. There are no rows to fill: documents of the
--      old Otak domain were moved to attachments in V1_2_125;
--   4. adds shrine.attachments.document_id, which links a snapshot of a document
--      to that document.
-- The enums shrine_document_type_enum and shrine_document_language_enum come
-- from V1_2_130.
--
-- shrine.documents.entity_id has no foreign key, as attachments.entity_id has
-- none: the owner table depends on entity_type. created_by_id and updated_by_id
-- of documents and of templates hold the id of the shrine.users row that
-- created and last changed the row. They carry no foreign key here, so that removing a user is never
-- blocked by the documents and templates the user wrote.
--
-- Delete rules:
--   document_templates_organization_id_fk ON DELETE CASCADE: deleting an
--     organization deletes its templates.
--   document_templates_legal_entity_id_organization_id_fk ON DELETE RESTRICT: a
--     legal entity that a template names cannot be deleted. The composite key
--     makes it impossible to name a legal entity of another organization. With
--     legal_entity_id NULL the key is not checked (MATCH SIMPLE).
--   documents_organization_id_fk ON DELETE CASCADE: deleting an organization
--     deletes its documents (not their blobs).
--   documents_legal_entity_id_organization_id_fk ON DELETE RESTRICT: a legal
--     entity with documents cannot be deleted. Its documents were signed in its
--     name and must not lose it.
--   documents_template_id_fk ON DELETE SET NULL: a document outlives the
--     template it was copied from, the link is provenance only.
--   attachments_document_id_fk ON DELETE SET NULL: a snapshot attachment
--     outlives its document.
--
-- Indexes:
--   document_templates_organization_id_lower_name_language_uk is unique per
--     organization on lower-case name and language, as it was in Otak. It
--     serves the list order and search and the organization foreign key and its
--     cascade.
--   document_templates_legal_entity_id_organization_id_idx serves the composite
--     legal entity foreign key and the RESTRICT check on a legal entity delete.
--   documents_organization_id_idx serves the organization foreign key, its
--     cascade, and the list of the documents of an organization.
--   documents_entity_type_entity_id_idx serves the documents of an owner.
--   documents_template_id_idx serves the template foreign key and its SET NULL.
--   documents_legal_entity_id_idx serves the documents of a legal entity and
--     the RESTRICT check on a legal entity delete.
--   attachments_document_id_idx serves the list of the snapshots of a document
--     and the SET NULL of the foreign key.

-- =====================================================
-- 1. Owner types of a document
-- =====================================================

CREATE TYPE shrine_document_entity_type_enum AS ENUM ('RELATION');

-- =====================================================
-- 2. Move document templates to shrine
-- =====================================================

CREATE TABLE shrine.document_templates (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    type shrine_document_type_enum NOT NULL,
    language shrine_document_language_enum NOT NULL,
    html_blob_url VARCHAR(500),
    pdf_blob_url VARCHAR(500),
    organization_id UUID NOT NULL,
    legal_entity_id UUID,
    created_by_id UUID NOT NULL,
    updated_by_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT document_templates_pk PRIMARY KEY (id),
    CONSTRAINT document_templates_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT document_templates_legal_entity_id_organization_id_fk FOREIGN KEY (legal_entity_id, organization_id) REFERENCES shrine.legal_entities(id, organization_id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX document_templates_organization_id_lower_name_language_uk ON shrine.document_templates USING btree (organization_id, lower((name)::text), language);
CREATE INDEX document_templates_legal_entity_id_organization_id_idx ON shrine.document_templates USING btree (legal_entity_id, organization_id);

DO $$
DECLARE
    missing_organization_id UUID;
BEGIN
    SELECT t.organization_id
    INTO missing_organization_id
    FROM otak.document_templates t
    WHERE NOT EXISTS (
        SELECT 1
        FROM shrine.organization_members m
        WHERE m.organization_id = t.organization_id
          AND m.role = 'OWNER'
    )
    LIMIT 1;

    IF missing_organization_id IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot move document templates: organization % has templates and no OWNER member to become their author', missing_organization_id;
    END IF;
END $$;

INSERT INTO shrine.document_templates (id, name, type, language, html_blob_url, pdf_blob_url, organization_id, created_by_id, updated_by_id, created_at, updated_at)
SELECT t.id, t.name, t.type, t.language, t.html_blob_url, t.pdf_blob_url, t.organization_id, o.user_id, o.user_id, t.created_at, t.updated_at
FROM otak.document_templates t
JOIN LATERAL (
    SELECT m.user_id
    FROM shrine.organization_members m
    WHERE m.organization_id = t.organization_id
      AND m.role = 'OWNER'
    ORDER BY m.created_at ASC, m.id ASC
    LIMIT 1
) o ON TRUE;

DROP TABLE otak.document_templates;

-- =====================================================
-- 3. Create documents
-- =====================================================

CREATE TABLE shrine.documents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    entity_type shrine_document_entity_type_enum NOT NULL,
    entity_id UUID NOT NULL,
    template_id UUID,
    title VARCHAR(200) NOT NULL,
    type shrine_document_type_enum NOT NULL,
    language shrine_document_language_enum NOT NULL,
    html_blob_url VARCHAR(500),
    pdf_blob_url VARCHAR(500),
    created_by_id UUID NOT NULL,
    updated_by_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT documents_pk PRIMARY KEY (id),
    CONSTRAINT documents_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT documents_legal_entity_id_organization_id_fk FOREIGN KEY (legal_entity_id, organization_id) REFERENCES shrine.legal_entities(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT documents_template_id_fk FOREIGN KEY (template_id) REFERENCES shrine.document_templates(id) ON DELETE SET NULL
);

CREATE INDEX documents_organization_id_idx ON shrine.documents USING btree (organization_id);
CREATE INDEX documents_entity_type_entity_id_idx ON shrine.documents USING btree (entity_type, entity_id);
CREATE INDEX documents_template_id_idx ON shrine.documents USING btree (template_id);
CREATE INDEX documents_legal_entity_id_idx ON shrine.documents USING btree (legal_entity_id);

-- =====================================================
-- 4. Link attachments to the document they are a snapshot of
-- =====================================================

ALTER TABLE shrine.attachments ADD COLUMN document_id UUID;

ALTER TABLE shrine.attachments
    ADD CONSTRAINT attachments_document_id_fk
    FOREIGN KEY (document_id) REFERENCES shrine.documents(id) ON DELETE SET NULL;

CREATE INDEX attachments_document_id_idx ON shrine.attachments USING btree (document_id);
