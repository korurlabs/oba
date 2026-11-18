-- Village Database Schema - Iroh Add Original Invoice Id To Invoices
-- V1_2_13__Iroh_Add_Original_Invoice_Id_To_Invoices.sql
--
-- Adds original_invoice_id FK for credit notes and debit notes that reverse prior invoices.
-- Used for audit trail and Dutch VAT return ICP corrections (factuurvereisten).

ALTER TABLE iroh.invoices ADD COLUMN original_invoice_id UUID;

ALTER TABLE iroh.invoices
ADD CONSTRAINT invoices_original_invoice_id_fk
    FOREIGN KEY (original_invoice_id) REFERENCES iroh.invoices(id);

CREATE INDEX invoices_original_invoice_id_idx ON iroh.invoices(original_invoice_id);
