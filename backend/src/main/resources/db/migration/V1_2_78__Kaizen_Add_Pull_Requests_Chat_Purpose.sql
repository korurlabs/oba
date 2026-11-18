-- Database Schema - Kaizen Add Pull Requests Chat Purpose
-- V1_2_78__Kaizen_Add_Pull_Requests_Chat_Purpose.sql
--
-- A channel with this purpose is where the team talks about pull requests. The
-- existing chat_channels_purpose_ck (public text channel) and
-- chat_channels_purpose_uk (one live holder per organization) already cover
-- every purpose other than GENERAL, so only the enum value is new.
--
-- The value gets a migration of its own: PostgreSQL cannot use a new enum value
-- in the transaction that adds it, so the channels that hold it are created in
-- the next migration.

ALTER TYPE kaizen_chat_channel_purpose_enum ADD VALUE IF NOT EXISTS 'PULL_REQUESTS';
