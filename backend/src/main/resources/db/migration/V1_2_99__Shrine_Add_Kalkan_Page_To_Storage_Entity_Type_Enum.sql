-- Oba Database Schema - Shrine: Add KALKAN_PAGE To Storage Entity Type Enum
-- V1_2_99__Shrine_Add_Kalkan_Page_To_Storage_Entity_Type_Enum.sql
--
-- Extends shrine_storage_entity_type_enum with KALKAN_PAGE so that
-- shrine.attachments and shrine.photos can be associated with Kalkan pages.
-- Alone in its file: a new enum value cannot be used in the transaction that adds it.

ALTER TYPE shrine_storage_entity_type_enum ADD VALUE IF NOT EXISTS 'KALKAN_PAGE';
