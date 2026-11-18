-- Village Database Schema - Add Bunq Per-Account Credentials
-- V1_2_18__Iroh_Add_Bunq_Per_Account_Credentials.sql
--
-- Adds per-account bunq credential and context columns to support multi-account integration via
-- the official bunq Java SDK. The encrypted_api_key stores the user's bunq API key for context
-- re-initialization if needed. The encrypted_bunq_context persists the full SDK ApiContext as
-- JSON to resume existing sessions without redoing the 3-step handshake. The bunq_user_id is
-- the numeric bunq user identifier required for API endpoint paths.

-- =====================================================
-- 1. Add bunq credential columns to bank_accounts
-- =====================================================

ALTER TABLE iroh.bank_accounts ADD COLUMN encrypted_api_key VARCHAR(1024);
ALTER TABLE iroh.bank_accounts ADD COLUMN encrypted_bunq_context TEXT;
ALTER TABLE iroh.bank_accounts ADD COLUMN bunq_user_id VARCHAR(50);

-- =====================================================
-- 2. Create index for bunq_user_id lookups
-- =====================================================

-- Partial index optimized for bunq-backed, non-deleted accounts only.
-- Enables efficient per-account context restoration and scheduler cross-referencing.
CREATE INDEX bank_accounts_bunq_user_id_idx
    ON iroh.bank_accounts(bunq_user_id)
    WHERE bunq_user_id IS NOT NULL AND deleted = FALSE;
