-- =================================================================
-- Shrine: Add user_preferences JSONB column to users table
-- =================================================================

-- Add nullable JSONB column to store per-user preference data.
ALTER TABLE shrine.users
    ADD COLUMN user_preferences JSONB NULL;
