-- Oba Database Schema - Shrine: Rename Document Enums And Add CONTRACT
-- V1_2_130__Shrine_Rename_Document_Enums_And_Add_Contract.sql
--
-- Document types and languages are shared by every app that stores documents,
-- no longer by Otak alone, so the two enum types change their prefix:
--   otak_document_type_enum      -> shrine_document_type_enum
--   otak_document_language_enum  -> shrine_document_language_enum
-- The columns that use them follow the rename without a rewrite:
-- otak.document_templates (type, language), otak.mail_templates (language) and
-- otak.mails (language). The next migration moves document_templates to the
-- shrine schema.
--
-- The document type enum gains CONTRACT, the type of a document signed by a
-- legal entity. This file holds nothing else: a new enum value cannot be used
-- in the transaction that adds it, and the next migration needs it.
--
-- Delete rules and indexes: none. No table or index changes.

ALTER TYPE otak_document_type_enum RENAME TO shrine_document_type_enum;
ALTER TYPE otak_document_language_enum RENAME TO shrine_document_language_enum;

ALTER TYPE shrine_document_type_enum ADD VALUE IF NOT EXISTS 'CONTRACT';
