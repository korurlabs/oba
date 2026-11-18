-- Village Database Schema - Add Discount Fields to Invoice Line Items
-- V1_2_56__Add_Discount_Fields_To_Invoice_Line_Items.sql
--
-- Adds two nullable discount columns to iroh.invoice_line_items:
-- discount_amount: monetary discount applied to the line (NUMERIC(12,2), matches total_excl_vat)
-- discount_percentage: percentage discount rate (NUMERIC(5,2), matches vat_percentage)
-- Both columns are nullable; existing rows stay NULL with no backfill needed.

-- =====================================================
-- 1. Add discount columns to invoice_line_items
-- =====================================================

ALTER TABLE iroh.invoice_line_items
    ADD COLUMN discount_amount     NUMERIC(12,2),
    ADD COLUMN discount_percentage NUMERIC(5,2);
