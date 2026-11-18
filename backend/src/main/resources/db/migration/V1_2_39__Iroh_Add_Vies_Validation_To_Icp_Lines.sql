-- Village Database Schema - Iroh Add VIES Validation To ICP Lines
-- V1_2_39__Iroh_Add_Vies_Validation_To_Icp_Lines.sql
--
-- Adds VIES (VAT Information Exchange System) validation status tracking to
-- iroh.icp_declaration_lines. When an ICP declaration is submitted, a background
-- Spring @Async task calls the EU VIES API for each line to verify the customer
-- VAT ID. The status starts as PENDING and transitions asynchronously to
-- VALID, INVALID, or ERROR (network/rate-limit failures).
--
-- flyway:executeInTransaction=false is required because CREATE TYPE cannot run
-- inside a transaction in all PostgreSQL scenarios.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum type
-- =====================================================

-- VIES validation lifecycle:
--   PENDING  — awaiting validation (default on insert)
--   VALID    — VAT ID confirmed valid by VIES
--   INVALID  — VAT ID confirmed invalid by VIES
--   ERROR    — validation attempt failed (network error, rate limit, etc.)
CREATE TYPE iroh_vies_validation_status_enum AS ENUM (
    'PENDING',
    'VALID',
    'INVALID',
    'ERROR'
);

-- =====================================================
-- 2. Add column to iroh.icp_declaration_lines
-- =====================================================

-- Non-blocking: DEFAULT 'PENDING' ensures existing rows (if any) receive a value
-- immediately; no backfill or table rewrite required.
ALTER TABLE iroh.icp_declaration_lines
    ADD COLUMN vies_validation_status iroh_vies_validation_status_enum NOT NULL DEFAULT 'PENDING';
