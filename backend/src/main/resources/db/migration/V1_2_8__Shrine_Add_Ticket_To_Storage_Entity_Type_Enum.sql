-- Village Database Schema - Shrine: Add TICKET To Storage Entity Type Enum
-- V1_2_8__Shrine_Add_Ticket_To_Storage_Entity_Type_Enum.sql
--
-- Extends shrine_storage_entity_type_enum with the TICKET value so that
-- shrine.attachments and shrine.photos can be associated with kaizen tickets.
-- Uses IF NOT EXISTS to make the migration safely re-runnable.

ALTER TYPE shrine_storage_entity_type_enum ADD VALUE IF NOT EXISTS 'TICKET';
