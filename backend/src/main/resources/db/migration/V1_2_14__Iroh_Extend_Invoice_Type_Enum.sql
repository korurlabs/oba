-- Village Database Schema - Iroh Extend Invoice Type Enum
-- V1_2_12__Iroh_Extend_Invoice_Type_Enum.sql
--
-- Adds CREDIT_NOTE and DEBIT_NOTE to iroh_invoice_type_enum.
-- Used to distinguish credit notes (creditnota/creditfactuur) and debit notes
-- (debetnota) for Dutch VAT return compliance and Peppol/UBL e-invoicing.

-- flyway:executeInTransaction=false

ALTER TYPE iroh_invoice_type_enum ADD VALUE IF NOT EXISTS 'CREDIT_NOTE';
ALTER TYPE iroh_invoice_type_enum ADD VALUE IF NOT EXISTS 'DEBIT_NOTE';
