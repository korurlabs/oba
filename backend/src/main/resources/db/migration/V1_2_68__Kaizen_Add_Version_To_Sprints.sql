-- Database Schema - Kaizen Add Version To Sprints
-- V1_2_68__Kaizen_Add_Version_To_Sprints.sql
--
-- Optimistic locking counter for kaizen.sprints. Two writers that load the same
-- sprint and save it one after the other no longer overwrite each other
-- silently: the second save fails. Existing rows start at 0, the same start
-- value as outbox_events.version.

ALTER TABLE kaizen.sprints
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
