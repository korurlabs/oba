-- Oba Database Schema - Otak: Drop Mail Sender Display Name
-- V1_2_113__Otak_Drop_Mail_Sender_Display_Name.sql
--
-- A mail sender is labelled by its mail address alone. The display name that
-- Microsoft 365 returned for the mailbox is no longer stored or returned, so
-- its column goes. No other object depends on the column.

-- =====================================================
-- 1. Drop the display name
-- =====================================================

ALTER TABLE otak.mail_senders DROP COLUMN display_name;
