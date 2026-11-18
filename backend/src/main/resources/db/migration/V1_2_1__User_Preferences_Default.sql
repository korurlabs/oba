-- Add nullable JSONB column with default user preferences
ALTER TABLE shrine.users
    ADD COLUMN IF NOT EXISTS user_preferences JSONB 
        DEFAULT '{"theme": "system"}'::jsonb;