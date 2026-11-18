-- Database Schema - Shrine Remove Unverified Admin Roles
-- V1_2_71__Shrine_Remove_Unverified_Admin_Roles.sql
--
-- Data migration. The ADMIN role in shrine.users_roles is about to gate the
-- user endpoints, but until now no check protected it: any user could write
-- the role to their own account through an unchecked PUT that carried a roles
-- list. Every existing ADMIN row is therefore unverified and is removed.
-- USER rows stay. An operator grants the first administrator afterwards with
-- one INSERT into shrine.users_roles.
--
-- No schema change: no table, column, constraint or index is added, so no
-- index is needed. The delete uses the primary key (user_id, role) table scan
-- once; the table is small.

DELETE FROM shrine.users_roles
WHERE role = 'ADMIN';
