-- Village Database Schema - Iroh Rename Vat Return Line Taxable Amount
-- V1_2_8__Iroh_Rename_Vat_Return_Line_Taxable_Amount.sql
--
-- Renames the column amount_excl_vat to taxable_amount in the
-- iroh.vat_return_lines table. The new name reflects the fiscal semantics
-- of the column: it holds the taxable base amount for the rubric.

-- =====================================================
-- 1. Rename column
-- =====================================================

ALTER TABLE iroh.vat_return_lines
    RENAME COLUMN amount_excl_vat TO taxable_amount;
