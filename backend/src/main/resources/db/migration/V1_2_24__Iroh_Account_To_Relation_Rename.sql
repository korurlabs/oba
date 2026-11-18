-- Village Database Schema - Iroh Account to Relation Rename
-- V1_2_22__Iroh_Account_To_Relation_Rename.sql
--
-- Promotes the iroh.accounts table to iroh.relations — a unified counterparty
-- model that covers suppliers, customers, dealers, and sales leads in one table.
--
-- Summary of operations (in dependency order):
--   1. Create iroh_relation_type_enum PostgreSQL enum
--   2. Rename iroh.accounts → iroh.relations
--   3. Rename all constraints and indexes on iroh.relations that referenced "accounts"
--   4. Rename the FK constraint on iroh.account_contacts that targets iroh.accounts
--   5. Rename the FK constraint on iroh.invoices that targets iroh.accounts
--   6. Add lead-specific nullable columns to iroh.relations
--   7. Create iroh.relation_types junction table
--   8. Migrate rows from iroh.sales_leads into iroh.relations with type LEAD
--   9. iroh.sales_leads is intentionally NOT dropped (deprecated; retained for rollback safety)
--
-- Invariants preserved by design (no migration work needed):
--   - iroh.invoices.account_id FK column name is unchanged (DB level)
--   - iroh.invoices.snapshot_account_* column names are unchanged (DB level)
--   - iroh.account_contacts table is NOT renamed (entity stays AccountContact)
--   - iroh.account_contacts.account_id FK column name is unchanged (DB level)
--
-- Cascade rules on relation_types:
--   - Deleting a relation cascades to its type rows (ON DELETE CASCADE)
--
-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum type
-- =====================================================

CREATE TYPE iroh_relation_type_enum AS ENUM (
    'SUPPLIER',
    'CUSTOMER',
    'DEALER',
    'LEAD'
);

-- =====================================================
-- 2. Rename iroh.accounts → iroh.relations
-- =====================================================

ALTER TABLE iroh.accounts RENAME TO relations;

-- =====================================================
-- 3. Rename constraints and indexes on iroh.relations
--    (were originally named after "accounts")
-- =====================================================

-- Primary key constraint
ALTER TABLE iroh.relations
    RENAME CONSTRAINT accounts_pk TO relations_pk;

-- Organization FK constraint
ALTER TABLE iroh.relations
    RENAME CONSTRAINT accounts_organization_id_fk TO relations_organization_id_fk;

-- Indexes: drop old names, create under new names.
-- PostgreSQL does not support ALTER INDEX ... RENAME inside the same schema when
-- the table has already been renamed, so we use DROP + CREATE to be safe.

DROP INDEX IF EXISTS iroh.accounts_organization_id_idx;
CREATE INDEX relations_organization_id_idx
    ON iroh.relations(organization_id);

DROP INDEX IF EXISTS iroh.accounts_organization_id_name_idx;
CREATE INDEX relations_organization_id_name_idx
    ON iroh.relations(organization_id, name);

-- =====================================================
-- 4. Rename FK constraint on iroh.account_contacts
--    (column name account_id is NOT changed)
-- =====================================================

ALTER TABLE iroh.account_contacts
    RENAME CONSTRAINT account_contacts_account_id_fk TO account_contacts_relation_id_fk;

-- =====================================================
-- 5. Rename FK constraint on iroh.invoices
--    (column name account_id is NOT changed)
-- =====================================================

ALTER TABLE iroh.invoices
    RENAME CONSTRAINT invoices_account_id_fk TO invoices_relation_id_fk;

-- =====================================================
-- 6. Add lead-specific nullable columns to iroh.relations
--    All columns are nullable — only lead rows populate them.
--    Column names mirror iroh.sales_leads for a clean INSERT ... SELECT.
-- =====================================================

-- Contact details (leads have a named contact person)
ALTER TABLE iroh.relations ADD COLUMN lead_first_name        VARCHAR(255);
ALTER TABLE iroh.relations ADD COLUMN lead_last_name         VARCHAR(255);

-- The accounts table already has email and phone columns.
-- Leads also carry email (NOT NULL in sales_leads) and phone_number (VARCHAR(30)).
-- We add lead_email and lead_phone_number as dedicated lead columns so that
-- the existing account email / phone columns remain unambiguous for non-lead rows.
ALTER TABLE iroh.relations ADD COLUMN lead_email             VARCHAR(255);
ALTER TABLE iroh.relations ADD COLUMN lead_phone_number      VARCHAR(30);

-- Company details
ALTER TABLE iroh.relations ADD COLUMN lead_company_name      VARCHAR(255);
ALTER TABLE iroh.relations ADD COLUMN lead_industry          VARCHAR(255);
ALTER TABLE iroh.relations ADD COLUMN lead_company_website   VARCHAR(2048);
ALTER TABLE iroh.relations ADD COLUMN lead_annual_revenue    VARCHAR(50);
ALTER TABLE iroh.relations ADD COLUMN lead_employee_count    INTEGER;

-- Sales funnel tracking
ALTER TABLE iroh.relations ADD COLUMN lead_status            iroh_lead_status_enum;
ALTER TABLE iroh.relations ADD COLUMN lead_first_contact_date     DATE;
ALTER TABLE iroh.relations ADD COLUMN lead_last_interaction_date  DATE;
ALTER TABLE iroh.relations ADD COLUMN lead_board_order       INTEGER;

-- ICP and presentation references (SET NULL on parent delete, same as sales_leads)
ALTER TABLE iroh.relations ADD COLUMN lead_icp_id            UUID;
ALTER TABLE iroh.relations ADD COLUMN lead_presentation_id   UUID;

-- FK constraints for lead_icp_id and lead_presentation_id
ALTER TABLE iroh.relations
    ADD CONSTRAINT relations_lead_icp_id_fk
        FOREIGN KEY (lead_icp_id) REFERENCES iroh.sales_icps(id) ON DELETE SET NULL;

ALTER TABLE iroh.relations
    ADD CONSTRAINT relations_lead_presentation_id_fk
        FOREIGN KEY (lead_presentation_id) REFERENCES iroh.sales_presentations(id) ON DELETE SET NULL;

-- Indexes for the lead FK columns
CREATE INDEX relations_lead_icp_id_idx
    ON iroh.relations(lead_icp_id);

CREATE INDEX relations_lead_presentation_id_idx
    ON iroh.relations(lead_presentation_id);

-- Composite index: lead status within an org (primary board/list view filter)
CREATE INDEX relations_organization_id_lead_status_idx
    ON iroh.relations(organization_id, lead_status);

-- =====================================================
-- 7. Create iroh.relation_types junction table
--    Stores 0..N types per relation.
--    Uses VARCHAR(50) for the type value (matches iroh_relation_type_enum labels).
-- =====================================================

CREATE TABLE iroh.relation_types (
    relation_id UUID        NOT NULL,
    type        VARCHAR(50) NOT NULL,
    CONSTRAINT relation_types_pk PRIMARY KEY (relation_id, type),
    CONSTRAINT relation_types_relation_id_fk
        FOREIGN KEY (relation_id) REFERENCES iroh.relations(id) ON DELETE CASCADE,
    -- Enforce that only valid enum values are inserted at the DB level.
    CONSTRAINT relation_types_type_ck
        CHECK (type IN ('SUPPLIER', 'CUSTOMER', 'DEALER', 'LEAD'))
);

CREATE INDEX relation_types_relation_id_idx
    ON iroh.relation_types(relation_id);

-- =====================================================
-- 8. Migrate iroh.sales_leads rows into iroh.relations
--    - name  = company_name  (relations.name is NOT NULL; leads have no single "name" field)
--    - lead_* columns populated from corresponding sales_leads columns
--    - All existing account rows get zero type rows (empty relation_types entries)
--    - Each migrated lead row gets a single LEAD entry in relation_types
-- =====================================================

-- 8a. Insert lead rows into relations.
--     Only non-deleted leads are migrated. Deleted leads are intentionally excluded
--     so the relations table starts clean; the full history remains in sales_leads.
INSERT INTO iroh.relations (
    id,
    organization_id,
    name,
    -- Standard account columns: nullable for leads
    vat_number,
    kvk_number,
    email,
    phone,
    website,
    iban,
    notes,
    is_active,
    address,
    postal_code,
    city,
    country,
    -- Lead-specific columns
    lead_first_name,
    lead_last_name,
    lead_email,
    lead_phone_number,
    lead_company_name,
    lead_industry,
    lead_company_website,
    lead_annual_revenue,
    lead_employee_count,
    lead_status,
    lead_first_contact_date,
    lead_last_interaction_date,
    lead_board_order,
    lead_icp_id,
    lead_presentation_id,
    -- Audit
    created_at,
    updated_at,
    deleted
)
SELECT
    sl.id,
    sl.organization_id,
    sl.company_name                 AS name,
    -- No equivalent fields in sales_leads for these account columns
    NULL::VARCHAR(20)               AS vat_number,
    NULL::VARCHAR(20)               AS kvk_number,
    NULL::VARCHAR(255)              AS email,
    NULL::VARCHAR(50)               AS phone,
    sl.company_website              AS website,
    NULL::VARCHAR(34)               AS iban,
    sl.notes,
    TRUE                            AS is_active,
    NULL::VARCHAR(500)              AS address,
    NULL::VARCHAR(10)               AS postal_code,
    NULL::VARCHAR(100)              AS city,
    NULL::VARCHAR(100)              AS country,
    -- Lead-specific columns
    sl.first_name                   AS lead_first_name,
    sl.last_name                    AS lead_last_name,
    sl.email                        AS lead_email,
    sl.phone_number                 AS lead_phone_number,
    sl.company_name                 AS lead_company_name,
    sl.industry                     AS lead_industry,
    sl.company_website              AS lead_company_website,
    sl.annual_revenue               AS lead_annual_revenue,
    sl.employee_count               AS lead_employee_count,
    sl.status                       AS lead_status,
    sl.first_contact_date           AS lead_first_contact_date,
    sl.last_interaction_date        AS lead_last_interaction_date,
    sl.board_order                  AS lead_board_order,
    sl.icp_id                       AS lead_icp_id,
    sl.presentation_id              AS lead_presentation_id,
    sl.created_at,
    sl.updated_at,
    sl.deleted
FROM iroh.sales_leads sl
WHERE sl.deleted = FALSE;

-- 8b. Insert LEAD type rows for every migrated lead.
INSERT INTO iroh.relation_types (relation_id, type)
SELECT sl.id, 'LEAD'
FROM iroh.sales_leads sl
WHERE sl.deleted = FALSE;

-- =====================================================
-- NOTE: iroh.sales_leads is intentionally NOT dropped.
-- It is deprecated and retained for rollback safety.
-- A follow-up migration (V1_2_23) will drop it after verification.
-- =====================================================
