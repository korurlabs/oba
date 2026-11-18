-- Database Schema - Add New Invoice Status Enum Values
-- V1_2_58__Iroh_Add_Invoice_Status_New_Values.sql
--
-- Adds 'SUBMITTED' and 'POSTED' to the existing iroh_invoice_status_enum type.
-- These values are needed before V1_2_59 can backfill data rows to them.
--
-- PostgreSQL requires ALTER TYPE ... ADD VALUE to run outside a transaction.
-- Flyway directive below disables the implicit transaction wrapper for this file.

-- flyway:executeInTransaction=false

ALTER TYPE iroh_invoice_status_enum ADD VALUE IF NOT EXISTS 'SUBMITTED' AFTER 'DRAFT';
ALTER TYPE iroh_invoice_status_enum ADD VALUE IF NOT EXISTS 'POSTED' AFTER 'SUBMITTED';
