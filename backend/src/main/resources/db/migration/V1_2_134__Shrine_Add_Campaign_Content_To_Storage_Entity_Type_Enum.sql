-- Oba Database Schema - Shrine: Add CAMPAIGN_CONTENT To Storage Entity Type Enum
-- V1_2_134__Shrine_Add_Campaign_Content_To_Storage_Entity_Type_Enum.sql
--
-- Extends shrine_storage_entity_type_enum with CAMPAIGN_CONTENT so that
-- shrine.attachments can be associated with Otak campaign contents.
-- Alone in its file: a new enum value cannot be used in the transaction that adds it.

ALTER TYPE shrine_storage_entity_type_enum ADD VALUE IF NOT EXISTS 'CAMPAIGN_CONTENT';
