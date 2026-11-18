-- Oba Database Schema - Kaizen: Require Ticket Mission Rank
-- V1_2_88__Kaizen_Require_Ticket_Mission_Rank.sql
--
-- Every ticket now carries a Mission Rank, the size estimate shown on the daily
-- update card. kaizen.tickets.mission_rank (kaizen_mission_rank_enum) becomes
-- NOT NULL with no default: every write goes through the entity, which always
-- sets the rank, and a missing value must fail instead of being guessed.

-- =====================================================
-- 1. Backfill missing ranks
-- =====================================================

-- M is the size most tickets have. Every row is updated, soft-deleted ones
-- included, because NOT NULL holds for all rows and a restored ticket must have
-- a rank. updated_at is left alone so that no ticket looks edited. A rerun
-- updates nothing.
UPDATE kaizen.tickets
SET mission_rank = 'M'
WHERE mission_rank IS NULL;

-- =====================================================
-- 2. Require the rank
-- =====================================================

ALTER TABLE kaizen.tickets
    ALTER COLUMN mission_rank SET NOT NULL;
