-- Village Database Schema - Kaizen: Add Mission Rank To Tickets
-- V1_1_18__Kaizen_Add_Mission_Rank_To_Tickets.sql
--
-- Converts the existing mission_rank column on kaizen.tickets from VARCHAR
-- to a proper PostgreSQL enum type (kaizen_mission_rank_enum).
-- Mission rank represents t-shirt size estimation (XS, S, M, L, XL, XXL).

-- =====================================================
-- 1. Create kaizen_mission_rank_enum
-- =====================================================

CREATE TYPE kaizen_mission_rank_enum AS ENUM ('XS', 'S', 'M', 'L', 'XL', 'XXL');

-- =====================================================
-- 2. Convert mission_rank column from VARCHAR to enum
-- =====================================================

ALTER TABLE kaizen.tickets
    ALTER COLUMN mission_rank TYPE kaizen_mission_rank_enum
    USING mission_rank::kaizen_mission_rank_enum;
