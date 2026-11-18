-- Oba Database Schema - Otak: Move Documents To Attachments
-- V1_2_125__Otak_Move_Documents_To_Attachments.sql
--
-- Files of a relation and of a mail are shrine attachments from now on, so the
-- Otak document tables go away:
--   RELATION attachments  one per file of every document: the PDF as
--                         '<name>.pdf' (application/pdf) and the HTML as
--                         '<name>.html' (text/html), on the relation of the
--                         document.
--   MAIL attachments      one per mail_attachments link whose document has a PDF
--                         (the send used only the PDF), on the mail of the link.
-- Each row keeps the blob URL and the timestamps of its document. file_size,
-- file_hash, width and height stay null: the blobs are not read here, and an
-- attachment without a hash is skipped by the duplicate check.
--
-- A blob can now back several attachment rows (a relation row and mail rows).
-- The application deletes a blob only when no other row holds its URL.
--
-- Delete rules: attachments carry no foreign key (entity_type and entity_id are
-- generic), so dropping mail_attachments and documents deletes only their own
-- rows; the attachment rows and the blobs stay. otak_document_type_enum and
-- otak_document_language_enum stay: mail and document templates use them.
--
-- Indexes: none added. attachments_entity_type_entity_id_idx (V1_0_6) serves the
-- list of a mail or a relation.

-- =====================================================
-- 1. Relation attachments from the document files
-- =====================================================

INSERT INTO shrine.attachments (id, url, file_name, content_type, entity_type, entity_id, created_at, updated_at)
SELECT gen_random_uuid(), d.pdf_blob_url, d.name || '.pdf', 'application/pdf',
       'RELATION'::shrine_storage_entity_type_enum, d.relation_id, d.created_at, d.updated_at
FROM otak.documents d
WHERE d.pdf_blob_url IS NOT NULL
ORDER BY d.created_at, d.id;

INSERT INTO shrine.attachments (id, url, file_name, content_type, entity_type, entity_id, created_at, updated_at)
SELECT gen_random_uuid(), d.html_blob_url, d.name || '.html', 'text/html',
       'RELATION'::shrine_storage_entity_type_enum, d.relation_id, d.created_at, d.updated_at
FROM otak.documents d
WHERE d.html_blob_url IS NOT NULL
ORDER BY d.created_at, d.id;

-- =====================================================
-- 2. Mail attachments from the links
-- =====================================================

INSERT INTO shrine.attachments (id, url, file_name, content_type, entity_type, entity_id, created_at, updated_at)
SELECT gen_random_uuid(), d.pdf_blob_url, d.name || '.pdf', 'application/pdf',
       'MAIL'::shrine_storage_entity_type_enum, ma.mail_id, d.created_at, d.updated_at
FROM otak.mail_attachments ma
JOIN otak.documents d ON d.id = ma.document_id
WHERE d.pdf_blob_url IS NOT NULL
ORDER BY d.created_at, d.id;

-- =====================================================
-- 3. Drop the document tables
-- =====================================================

DROP TABLE otak.mail_attachments;
DROP TABLE otak.documents;
