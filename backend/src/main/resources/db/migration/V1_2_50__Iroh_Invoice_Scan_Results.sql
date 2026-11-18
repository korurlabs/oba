-- Village Database Schema - Iroh Invoice Scan Results
-- V1_2_50__Iroh_Invoice_Scan_Results.sql
--
-- Adds two JSONB columns to iroh.invoices to persist Claude AI scan and review
-- results alongside the invoice record:
--
--   scan_result        — stores the InvoiceScanResponse payload produced by the
--                        initial AI extraction pass (populated for PURCHASE invoices
--                        where OCR scanning is performed; NULL for SALES invoices).
--
--   scan_review_result — stores the InvoiceScanReviewResponse payload produced by
--                        the dual-AI consensus review pass (populated only when a
--                        review is triggered after the initial scan; NULL otherwise).
--
-- Both columns are nullable because SALES invoices are not scanned and review is
-- an optional second pass. Using JSONB (rather than TEXT or BYTEA) keeps the
-- payloads queryable and indexable if future features require it.

-- =====================================================
-- 1. Extend iroh.invoices with AI scan result columns
-- =====================================================

ALTER TABLE iroh.invoices
    ADD COLUMN scan_result        JSONB,
    ADD COLUMN scan_review_result JSONB;
