-- Village Database Schema - Iroh Add Source Invoice Line Item To Journal Entry Lines
-- V1_2_9__Iroh_Add_Source_Invoice_Line_Item_To_Journal_Entry_Lines.sql
--
-- Adds a nullable many-to-one link from journal_entry_lines back to the
-- invoice_line_items that triggered them. Nullable because manual journal
-- entries and other non-invoice-sourced postings (opening balances,
-- adjustments, bank entries) do not originate from an invoice line.
-- Many-to-one because a single invoice line typically fans out into
-- multiple journal entry lines (revenue, VAT, discounts), all sharing the
-- same source invoice line item. Provides traceability from the general
-- ledger back to the originating invoice line for reconciliation and audit.

-- =====================================================
-- 1. Add source_invoice_line_item_id column with FK
-- =====================================================

ALTER TABLE iroh.journal_entry_lines
    ADD COLUMN source_invoice_line_item_id UUID;

ALTER TABLE iroh.journal_entry_lines
    ADD CONSTRAINT journal_entry_lines_source_invoice_line_item_id_fk
        FOREIGN KEY (source_invoice_line_item_id)
        REFERENCES iroh.invoice_line_items(id);

-- =====================================================
-- 2. Create index on the new foreign key
-- =====================================================

CREATE INDEX journal_entry_lines_source_invoice_line_item_id_idx
    ON iroh.journal_entry_lines(source_invoice_line_item_id);
