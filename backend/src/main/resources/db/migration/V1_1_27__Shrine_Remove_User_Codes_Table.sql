-- Remove UserCode domain: drop table and enum type
-- The invitation flow now uses the user_invitations table exclusively.

DROP INDEX IF EXISTS users_codes_invitation_email_idx;

DROP TABLE IF EXISTS shrine.users_codes;

DROP TYPE IF EXISTS shrine_identity_user_code_type_enum;
