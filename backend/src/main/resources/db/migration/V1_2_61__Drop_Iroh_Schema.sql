-- Village Database Schema - Drop Iroh Schema
-- V1_2_61__Drop_Iroh_Schema.sql
--
-- The Iroh app (finance/invoicing/sales/payroll domain) has been fully removed from
-- the Village backend's Java codebase. This migration drops the entire database
-- footprint that Iroh accumulated across 51 prior migrations (V1_2_2 through
-- V1_2_60, with two additional non-"Iroh"-named migrations — V1_2_4 and V1_2_56 —
-- that also touched iroh.invoices / iroh.invoice_line_items).
--
-- Inventory confirmed by inspecting every Iroh-related migration plus every
-- interspersed migration in the V1_2_x range (V1_2_3, V1_2_7-9, V1_2_51-54, V1_2_56)
-- to rule out cross-app dependencies:
--   - All 47 Iroh tables live exclusively in the dedicated "iroh" PostgreSQL schema
--     (accounts/relations, invoices + line items, VAT returns/supplements, ledger +
--     journal entries, bank accounts/mutations/reconciliation, sales CRM (ICPs,
--     presentations, leads, board columns, message templates, messages, discovery
--     runs, appointments, newsletters), payroll (employees, periods, payslips,
--     declarations), assets + depreciation, annual filings, fiscal year data,
--     compliance obligations, Kraken crypto integration).
--   - No migration outside the Iroh set references the "iroh" schema, an
--     "iroh_*" table, or an "iroh_*_enum" type (confirmed via full-directory grep).
--     kaizen/lancer/sage/shrine/korur migrations are entirely independent.
--   - All iroh_*_enum types were created WITHOUT a schema qualifier, so they live in
--     the default "public" schema, NOT inside the "iroh" schema. Dropping the "iroh"
--     schema does not remove them — they must be dropped explicitly (see section 2).
--   - iroh.enforce_vat_return_immutability() (a trigger function + trigger on
--     iroh.vat_returns, added in V1_2_40) lives inside the "iroh" schema and is
--     removed automatically by the schema drop.
--   - iroh.sales_leads was superseded by iroh.relations (V1_2_22/24) but was
--     deliberately never dropped by a follow-up migration ("retained for rollback
--     safety" per V1_2_22's comment) — it is still present and is dropped here along
--     with the rest of the schema.
--
-- Known residual (intentionally NOT touched by this migration):
--   V1_2_2 added the value 'INVOICE' to the shared shrine.shrine_storage_entity_type_enum
--   (used by shrine.attachments.entity_type, which also serves 'ORGANIZATION', 'USER',
--   and 'TICKET'). PostgreSQL cannot drop a single enum value without the full
--   rename/recreate/cast dance, and that type is actively used by non-Iroh (shrine)
--   data. The 'INVOICE' label will remain defined but unused after this migration —
--   harmless, and consistent with how shared enums are handled elsewhere in this
--   codebase (see V1_2_53 leaving shrine.approval_subject_type_enum values it wasn't
--   asked to touch).
--
-- Cascade rationale:
--   DROP SCHEMA ... CASCADE is required because the schema is non-empty; CASCADE
--   drops every table, index, constraint, sequence, function, and trigger inside it
--   in dependency-safe order automatically. No object outside the "iroh" schema
--   depends on anything inside it (verified above), so CASCADE cannot reach into
--   shrine or any other app's schema.

-- =====================================================
-- 1. Drop the iroh schema and everything inside it
--    (47 tables, all their indexes/constraints, the journal_entry_sequences
--    counter table, and the enforce_vat_return_immutability() trigger + function)
-- =====================================================

DROP SCHEMA IF EXISTS iroh CASCADE;

-- =====================================================
-- 2. Drop Iroh-owned enum types
--    These were created without a schema qualifier (default search_path = public)
--    so they survive the schema drop above and must be dropped explicitly.
--    Safe now: every column that referenced them was dropped in step 1.
-- =====================================================

DROP TYPE IF EXISTS iroh_accounting_period_status_enum;
DROP TYPE IF EXISTS iroh_annual_filing_status_enum;
DROP TYPE IF EXISTS iroh_annual_filing_type_enum;
DROP TYPE IF EXISTS iroh_appointment_type_enum;
DROP TYPE IF EXISTS iroh_asset_status_enum;
DROP TYPE IF EXISTS iroh_asset_type_enum;
DROP TYPE IF EXISTS iroh_bank_mutation_status_enum;
DROP TYPE IF EXISTS iroh_depreciation_method_enum;
DROP TYPE IF EXISTS iroh_depreciation_status_enum;
DROP TYPE IF EXISTS iroh_discovery_run_status_enum;
DROP TYPE IF EXISTS iroh_employee_status_enum;
DROP TYPE IF EXISTS iroh_filing_channel_enum;
DROP TYPE IF EXISTS iroh_icp_declaration_status_enum;
DROP TYPE IF EXISTS iroh_icp_supply_type_enum;
DROP TYPE IF EXISTS iroh_invoice_direction_enum;
DROP TYPE IF EXISTS iroh_invoice_payment_status_enum;
DROP TYPE IF EXISTS iroh_invoice_status_enum;
DROP TYPE IF EXISTS iroh_invoice_type_enum;
DROP TYPE IF EXISTS iroh_journal_entry_source_type_enum;
DROP TYPE IF EXISTS iroh_journal_entry_status_enum;
DROP TYPE IF EXISTS iroh_kraken_order_type_enum;
DROP TYPE IF EXISTS iroh_kraken_trade_status_enum;
DROP TYPE IF EXISTS iroh_kraken_trade_type_enum;
DROP TYPE IF EXISTS iroh_lead_status_enum;
DROP TYPE IF EXISTS iroh_ledger_account_template_enum;
DROP TYPE IF EXISTS iroh_legal_form_enum;
DROP TYPE IF EXISTS iroh_message_tone_enum;
DROP TYPE IF EXISTS iroh_newsletter_status_enum;
DROP TYPE IF EXISTS iroh_obligation_status_enum;
DROP TYPE IF EXISTS iroh_obligation_type_enum;
DROP TYPE IF EXISTS iroh_payroll_declaration_status_enum;
DROP TYPE IF EXISTS iroh_payroll_period_status_enum;
DROP TYPE IF EXISTS iroh_payroll_period_type_enum;
DROP TYPE IF EXISTS iroh_payroll_tax_table_enum;
DROP TYPE IF EXISTS iroh_payslip_status_enum;
DROP TYPE IF EXISTS iroh_presentation_type_enum;
DROP TYPE IF EXISTS iroh_proposal_confidence_level_enum;
DROP TYPE IF EXISTS iroh_proposal_status_enum;
DROP TYPE IF EXISTS iroh_proposal_type_enum;
DROP TYPE IF EXISTS iroh_relation_source_enum;
DROP TYPE IF EXISTS iroh_relation_type_enum;
DROP TYPE IF EXISTS iroh_sales_message_status_enum;
DROP TYPE IF EXISTS iroh_vat_category_enum;
DROP TYPE IF EXISTS iroh_vat_period_enum;
DROP TYPE IF EXISTS iroh_vat_return_status_enum;
DROP TYPE IF EXISTS iroh_vat_rubric_enum;
DROP TYPE IF EXISTS iroh_vat_supplement_status_enum;
DROP TYPE IF EXISTS iroh_vies_validation_status_enum;
