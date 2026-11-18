-- Database Schema - Shrine Rename Organization Role And Drop Users Roles
-- V1_2_94__Shrine_Rename_Organization_Role_And_Drop_Users_Roles.sql
--
-- 1. The Java enum OrganizationRole becomes OrganizationMemberRole, so the
--    PostgreSQL type takes the matching name. RENAME TO keeps the type and its
--    values; shrine.organization_members.role (and its default),
--    shrine.user_invitations.role and shrine.users_codes.invitation_role follow the type
--    automatically, and no stored row changes.
--
-- 2. shrine.users_roles held the platform-wide USER and ADMIN roles. Platform
--    administration is removed and organization access is decided only by the
--    member role, so nothing reads the table any more. Dropping it removes its
--    primary key, its foreign key to shrine.users (ON DELETE CASCADE, which only
--    ever deleted rows of this table) and its unique key. No other table
--    references it, so no dependent row is affected.
--
-- No index is added: the migration creates no column or constraint.

ALTER TYPE shrine_organization_role_enum RENAME TO shrine_organization_member_role_enum;

DROP TABLE shrine.users_roles;
