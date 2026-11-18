-- Village Database Schema - Drop Betik Schema
-- V1_2_3__Drop_Betik_Schema.sql
--
-- Drops the legacy betik schema and all its associated enum types from the
-- public schema. The betik domain has been superseded and is no longer needed.
-- CASCADE handles all dependent objects (tables, functions, triggers, sequences).

-- =====================================================
-- 1. Drop schema (cascades to all tables, functions, triggers)
-- =====================================================

DROP SCHEMA IF EXISTS betik CASCADE;

-- =====================================================
-- 2. Drop orphaned enum types (public schema)
-- =====================================================

-- Account domain
DROP TYPE IF EXISTS betik_account_type_enum;
DROP TYPE IF EXISTS betik_account_category_enum;

-- Invoice domain
DROP TYPE IF EXISTS betik_invoice_direction_enum;
DROP TYPE IF EXISTS betik_invoice_status_enum;
DROP TYPE IF EXISTS betik_vat_rate_type_enum;

-- Journal entry domain
DROP TYPE IF EXISTS betik_journal_entry_type_enum;

-- VAT and annual return domains
DROP TYPE IF EXISTS betik_vat_return_status_enum;
DROP TYPE IF EXISTS betik_annual_return_status_enum;

-- Data import domain
DROP TYPE IF EXISTS betik_import_source_enum;
DROP TYPE IF EXISTS betik_import_status_enum;

-- Organization settings domain
DROP TYPE IF EXISTS betik_company_type_enum;

-- Relations domain
DROP TYPE IF EXISTS betik_relation_type_enum;

-- Quotes domain
DROP TYPE IF EXISTS betik_quote_status_enum;

-- Tasks domain
DROP TYPE IF EXISTS betik_task_type_enum;
DROP TYPE IF EXISTS betik_task_status_enum;
DROP TYPE IF EXISTS betik_task_priority_enum;

-- Bank transactions domain
DROP TYPE IF EXISTS betik_match_status_enum;

-- Bank features domain (V1_0_5)
DROP TYPE IF EXISTS betik_csv_profile_enum;
DROP TYPE IF EXISTS betik_bunq_connection_status_enum;
