-- Database Schema - Add converted_to_customer_at to Relations
-- V1_2_60__Iroh_Relation_Add_Converted_At.sql
--
-- Adds a nullable audit timestamp to iroh.relations that is written exactly once
-- when a Lead relation is converted to a Customer. The column intentionally has
-- no default and no NOT NULL constraint so that:
--   a) all existing rows remain untouched, and
--   b) non-lead relations that are never converted keep a NULL value, making
--      the absence of a conversion explicitly readable.

-- =====================================================
-- 1. Add column
-- =====================================================

ALTER TABLE iroh.relations
    ADD COLUMN converted_to_customer_at TIMESTAMPTZ;
