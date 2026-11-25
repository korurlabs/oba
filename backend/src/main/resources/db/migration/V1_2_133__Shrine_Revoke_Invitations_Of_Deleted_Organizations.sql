-- Database Schema - Shrine Revoke Invitations Of Deleted Organizations
-- V1_2_133__Shrine_Revoke_Invitations_Of_Deleted_Organizations.sql
--
-- Data only. A deleted organization can no longer be joined, so an invitation
-- to it must not stay pending; deleting an organization now revokes its pending
-- invitations. Organizations deleted before that kept theirs, and this revokes
-- them the same way. Only pending invitations of soft-deleted organizations
-- change: an accepted, declined, expired or revoked invitation keeps its status,
-- and running the statement again changes nothing.
UPDATE shrine.user_invitations i
   SET status = 'REVOKED'::shrine_identity_user_invitation_status_enum, updated_at = now()
  FROM shrine.organizations o
 WHERE o.id = i.organization_id
   AND o.deleted = TRUE
   AND i.status = 'PENDING'::shrine_identity_user_invitation_status_enum;
