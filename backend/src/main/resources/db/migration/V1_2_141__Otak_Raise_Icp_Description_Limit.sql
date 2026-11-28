-- Oba Database Schema - Otak: Raise ICP Description Limit
-- V1_2_141__Otak_Raise_Icp_Description_Limit.sql
--
-- An ICP description is Markdown and a full ICP document runs past 10,000
-- characters. The column is already TEXT; only the check constraint caps it.
-- This migration replaces that constraint with one that allows 20,000
-- characters. Existing rows are at most 10,000 characters, so the new check
-- validates without rejecting any row.

-- =====================================================
-- 1. Replace the description length check
-- =====================================================

ALTER TABLE otak.icps
    DROP CONSTRAINT icps_description_ck;

ALTER TABLE otak.icps
    ADD CONSTRAINT icps_description_ck CHECK ((char_length(description) <= 20000));
