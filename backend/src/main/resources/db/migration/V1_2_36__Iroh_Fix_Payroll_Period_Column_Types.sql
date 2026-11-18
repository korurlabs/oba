-- Village Database Schema - Fix Payroll Period Column Types
-- V1_2_36__Iroh_Fix_Payroll_Period_Column_Types.sql
--
-- Corrects the column types in iroh.payroll_periods to match the PayrollPeriod
-- entity definition. The original migration V1_2_28 created columns year and
-- period_number as SMALLINT, but the entity defines them as Integer (which maps
-- to PostgreSQL INTEGER / 4-byte int).
--
-- This migration changes the column types from SMALLINT (2-byte) to INTEGER (4-byte)
-- to align the database schema with the JPA entity definition.

-- =====================================================
-- 1. Change column types from SMALLINT to INTEGER
-- =====================================================

ALTER TABLE iroh.payroll_periods
    ALTER COLUMN year TYPE INTEGER;

ALTER TABLE iroh.payroll_periods
    ALTER COLUMN period_number TYPE INTEGER;
