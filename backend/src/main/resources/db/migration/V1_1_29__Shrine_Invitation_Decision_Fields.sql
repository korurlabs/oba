-- Add DECLINED to invitation status enum
ALTER TYPE shrine_identity_user_invitation_status_enum ADD VALUE IF NOT EXISTS 'DECLINED';

-- Add expiry and declined timestamp columns
ALTER TABLE shrine.user_invitations ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE shrine.user_invitations ADD COLUMN IF NOT EXISTS declined_at TIMESTAMP WITH TIME ZONE NULL;
