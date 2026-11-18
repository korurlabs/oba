-- Oba Database Schema - Rename The Kaizen App To Kazan
-- V1_2_127__Kazan_Rename_Kaizen_Schema_Types_And_Values.sql
--
-- The Oba app Kaizen is now called Kazan. This migration renames every
-- database object that carries the old name: the schema, the sixteen
-- kaizen_*_enum types, and the two enum values KAIZEN and KAIZEN_TICKET.
-- Applied migrations keep the old name; they are history and Flyway
-- validates their checksums.
--
-- All statements are renames. Tables, columns, indexes, constraints and
-- foreign keys are untouched: PostgreSQL moves them with the schema, and
-- columns, defaults and partial index predicates follow a renamed type by
-- object id, so no data is rewritten. None of those objects has the old name
-- in its own name.
--
-- Delete rules: none change. Every foreign key keeps its ON DELETE rule
-- (CASCADE or SET NULL) because the constraints are not recreated.
--
-- Indexes: none are added or dropped, so no query plan changes.
--
-- Push devices: the mobile app moves to a new Expo project, so every stored
-- Expo push token belongs to the former project and can no longer be
-- delivered to. The rows are deleted; each device registers its token again
-- on the next sign-in. shrine.push_devices has no dependent rows
-- (push_preferences references users, not devices).
--
-- Stored text: links to https://kaizen.korur.com/ in free-text columns are
-- rewritten to https://kazan.korur.com/ so that saved links keep working.
-- The new name is one character shorter, so no length limit can be exceeded.
-- shrine.attachments.url holds blob storage URLs, never an app link, so it
-- is not rewritten.
--
-- The migration is not backward compatible with the previous application
-- version, which reads the old names. The release that carries it is a hard
-- cutover.

-- =====================================================
-- 1. Rename the schema
-- =====================================================

ALTER SCHEMA kaizen RENAME TO kazan;

-- =====================================================
-- 2. Rename the enum types
-- =====================================================

ALTER TYPE kaizen_board_member_role_enum RENAME TO kazan_board_member_role_enum;
ALTER TYPE kaizen_chat_channel_purpose_enum RENAME TO kazan_chat_channel_purpose_enum;
ALTER TYPE kaizen_chat_channel_type_enum RENAME TO kazan_chat_channel_type_enum;
ALTER TYPE kaizen_chat_channel_visibility_enum RENAME TO kazan_chat_channel_visibility_enum;
ALTER TYPE kaizen_chat_thread_status_enum RENAME TO kazan_chat_thread_status_enum;
ALTER TYPE kaizen_milestone_status_enum RENAME TO kazan_milestone_status_enum;
ALTER TYPE kaizen_mission_rank_enum RENAME TO kazan_mission_rank_enum;
ALTER TYPE kaizen_pull_request_share_status_enum RENAME TO kazan_pull_request_share_status_enum;
ALTER TYPE kaizen_sprint_recurrence_creation_mode_enum RENAME TO kazan_sprint_recurrence_creation_mode_enum;
ALTER TYPE kaizen_sprint_recurrence_run_status_enum RENAME TO kazan_sprint_recurrence_run_status_enum;
ALTER TYPE kaizen_sprint_recurrence_ticket_action_enum RENAME TO kazan_sprint_recurrence_ticket_action_enum;
ALTER TYPE kaizen_sprint_status_enum RENAME TO kazan_sprint_status_enum;
ALTER TYPE kaizen_system_actor_type_enum RENAME TO kazan_system_actor_type_enum;
ALTER TYPE kaizen_ticket_priority_enum RENAME TO kazan_ticket_priority_enum;
ALTER TYPE kaizen_ticket_status_enum RENAME TO kazan_ticket_status_enum;
ALTER TYPE kaizen_ticket_type_enum RENAME TO kazan_ticket_type_enum;

-- =====================================================
-- 3. Rename the enum values
-- =====================================================

ALTER TYPE shrine_app_grant_app_enum RENAME VALUE 'KAIZEN' TO 'KAZAN';
ALTER TYPE session_session_link_kind_enum RENAME VALUE 'KAIZEN_TICKET' TO 'KAZAN_TICKET';

-- =====================================================
-- 4. Remove the push tokens of the former mobile project
-- =====================================================

DELETE FROM shrine.push_devices;

-- =====================================================
-- 5. Rewrite stored links to the old host name
-- =====================================================

UPDATE kazan.tickets
SET description = replace(description, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE description LIKE '%kaizen.korur.com%';

UPDATE kazan.tickets
SET rca = replace(rca, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE rca LIKE '%kaizen.korur.com%';

UPDATE kazan.tickets
SET lessons_learned = replace(lessons_learned, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE lessons_learned LIKE '%kaizen.korur.com%';

UPDATE kazan.comments
SET content = replace(content, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE content LIKE '%kaizen.korur.com%';

UPDATE kazan.chat_messages
SET content = replace(content, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE content LIKE '%kaizen.korur.com%';

UPDATE kazan.boards
SET description = replace(description, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE description LIKE '%kaizen.korur.com%';

UPDATE kazan.milestones
SET description = replace(description, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE description LIKE '%kaizen.korur.com%';

UPDATE kazan.sprints
SET goal = replace(goal, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE goal LIKE '%kaizen.korur.com%';

UPDATE kazan.daily_updates
SET focus = replace(focus, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE focus LIKE '%kaizen.korur.com%';

UPDATE kazan.daily_updates
SET questions = replace(questions, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE questions LIKE '%kaizen.korur.com%';

UPDATE kazan.daily_update_tickets
SET note = replace(note, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE note LIKE '%kaizen.korur.com%';

UPDATE kazan.pull_request_shares
SET note = replace(note, 'https://kaizen.korur.com/', 'https://kazan.korur.com/')
WHERE note LIKE '%kaizen.korur.com%';
