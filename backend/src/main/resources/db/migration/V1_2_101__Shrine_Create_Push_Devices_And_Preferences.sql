-- Oba Database Schema - Shrine: Create Push Devices And Preferences
-- V1_2_101__Shrine_Create_Push_Devices_And_Preferences.sql
--
-- The mobile app receives push notifications through Expo. push_devices holds
-- one row per Expo push token, push_preferences one row per user with the
-- switches that decide which pushes the user gets. A user without a
-- push_preferences row has every switch on.
--
-- Delete rules:
--   push_devices_user_id_fk ON DELETE CASCADE: a deleted user has no devices
--     left to push to.
--   push_preferences_user_id_fk ON DELETE CASCADE: preferences have no meaning
--     without the user.
--
-- Indexes:
--   push_devices_expo_push_token_uk: a token is registered once; registering it
--     again under another user moves the row, so a handed-over phone never
--     receives the previous user's pushes.
--   push_devices_user_id_idx: the dispatcher loads all devices of a recipient,
--     and the index serves the user foreign key cascade.
--   push_preferences_user_id_uk: one row per user; it also serves the lookup
--     and the foreign key cascade, so no separate index is added.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE shrine_push_platform_enum AS ENUM ('IOS', 'ANDROID');

-- =====================================================
-- 2. Create tables
-- =====================================================

-- locale is the language of the push copy. last_registered_at is refreshed each
-- time the app registers the token.
CREATE TABLE shrine.push_devices (
    id UUID NOT NULL,
    expo_push_token VARCHAR(255) NOT NULL,
    platform shrine_push_platform_enum NOT NULL,
    device_name VARCHAR(100) NULL,
    app_version VARCHAR(32) NOT NULL,
    locale VARCHAR(2) NOT NULL,
    last_registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT push_devices_pk PRIMARY KEY (id),
    CONSTRAINT push_devices_expo_push_token_uk UNIQUE (expo_push_token),
    CONSTRAINT push_devices_expo_push_token_ck CHECK (expo_push_token ~ '^ExponentPushToken\[.+\]$'),
    CONSTRAINT push_devices_locale_ck CHECK (locale IN ('en', 'nl', 'tr')),
    CONSTRAINT push_devices_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

CREATE TABLE shrine.push_preferences (
    id UUID NOT NULL,
    mentions_and_replies BOOLEAN NOT NULL DEFAULT TRUE,
    everyone BOOLEAN NOT NULL DEFAULT TRUE,
    here BOOLEAN NOT NULL DEFAULT TRUE,
    urgent_incidents BOOLEAN NOT NULL DEFAULT TRUE,
    show_previews BOOLEAN NOT NULL DEFAULT TRUE,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT push_preferences_pk PRIMARY KEY (id),
    CONSTRAINT push_preferences_user_id_uk UNIQUE (user_id),
    CONSTRAINT push_preferences_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users (id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE INDEX push_devices_user_id_idx ON shrine.push_devices (user_id);
