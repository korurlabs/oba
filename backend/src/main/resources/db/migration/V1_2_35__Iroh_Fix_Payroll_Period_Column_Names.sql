-- Village Database Schema - Fix Payroll Period Column Names
-- V1_2_35__Iroh_Fix_Payroll_Period_Column_Names.sql
--
-- Corrects the column names in iroh.payroll_periods to match the PayrollPeriod
-- entity definition. The original migration V1_2_28 created columns named
-- start_date and end_date, but the entity expects period_start and period_end
-- to align with the naming convention (period_type, period_number, etc.).
--
-- This migration renames the columns to align the database schema with the
-- JPA entity definition.

-- =====================================================
-- 1. Rename columns to match entity definition
-- =====================================================

ALTER TABLE iroh.payroll_periods
    RENAME COLUMN start_date TO period_start;

ALTER TABLE iroh.payroll_periods
    RENAME COLUMN end_date TO period_end;

-- =====================================================
-- 2. Update the check constraint to reference new column name
-- =====================================================

ALTER TABLE iroh.payroll_periods
    DROP CONSTRAINT payroll_periods_date_range_ck;

ALTER TABLE iroh.payroll_periods
    ADD CONSTRAINT payroll_periods_date_range_ck
        CHECK (period_end >= period_start);
