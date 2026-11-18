-- Village Database Schema - Shrine: Add CHAT_MESSAGE To Storage Entity Type Enum
-- V1_2_75__Shrine_Add_Chat_Message_To_Storage_Entity_Type_Enum.sql
--
-- Extends shrine_storage_entity_type_enum with the CHAT_MESSAGE value so that
-- shrine.attachments can be associated with Kaizen chat messages.
-- It is alone in its file because PostgreSQL refuses to use a new enum value in
-- the transaction that adds it. Uses IF NOT EXISTS to make the migration safely
-- re-runnable.

ALTER TYPE shrine_storage_entity_type_enum ADD VALUE IF NOT EXISTS 'CHAT_MESSAGE';
