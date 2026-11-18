-- Village Database Schema - Iroh Add Invoice Line Vat Impacts
-- V1_2_11__Iroh_Add_Invoice_Line_Vat_Impacts.sql
--
-- Creates the invoice_line_vat_impacts table. Each row represents the impact
-- of one InvoiceLineItem on one Dutch BTW return rubriek. One line item can
-- produce multiple rows (e.g. EU reverse-charge creates both R_4B and R_5B).
-- Rows are regenerated while the parent invoice is DRAFT and locked on OPEN.

-- =====================================================
-- 1. Create invoice_line_vat_impacts table
-- =====================================================

CREATE TABLE iroh.invoice_line_vat_impacts (
    id                      UUID                     NOT NULL DEFAULT gen_random_uuid(),
    organization_id         UUID                     NOT NULL,
    invoice_line_item_id    UUID                     NOT NULL,
    vat_rubric              iroh_vat_rubric_enum     NOT NULL,
    taxable_amount_excl_vat NUMERIC(12,2)            NOT NULL DEFAULT 0,
    vat_amount              NUMERIC(12,2)            NOT NULL DEFAULT 0,
    reason                  VARCHAR(500),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted                 BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT invoice_line_vat_impacts_pk PRIMARY KEY (id),
    CONSTRAINT invoice_line_vat_impacts_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT invoice_line_vat_impacts_invoice_line_item_id_fk
        FOREIGN KEY (invoice_line_item_id) REFERENCES iroh.invoice_line_items(id)
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Supports the primary query: fetch all impacts for a given invoice line item.
CREATE INDEX invoice_line_vat_impacts_invoice_line_item_id_idx
    ON iroh.invoice_line_vat_impacts(invoice_line_item_id);

CREATE INDEX invoice_line_vat_impacts_organization_id_idx
    ON iroh.invoice_line_vat_impacts(organization_id);

-- Supports the VAT return period query: filter by organization + rubric.
CREATE INDEX invoice_line_vat_impacts_organization_id_vat_rubric_idx
    ON iroh.invoice_line_vat_impacts(organization_id, vat_rubric);
