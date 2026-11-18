-- Village Database Schema - ContactPerson Domain + Relation Cleanup
-- V1_2_31__Iroh_Add_ContactPerson.sql
--
-- Promotes RelationContact to a proper ContactPerson sub-domain:
--   1. Rename iroh.account_contacts → iroh.contact_persons
--   2. Rename FK column account_id → relation_id
--   3. Rename FK constraint and indexes to match new names
--   4. Drop redundant lead_* columns from iroh.relations (firstName, lastName, email, etc. now on ContactPerson)
--   5. Rename remaining lead_* columns on iroh.relations to unprefixed names (industry, status, etc.)
--
-- Invariants preserved:
--   - ContactPerson (formerly RelationContact) maintains all existing functionality
--   - Lead funnel fields stay on Relation entity with clean column names
--
-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Rename account_contacts → contact_persons
-- =====================================================

ALTER TABLE iroh.account_contacts RENAME TO contact_persons;

-- =====================================================
-- 2. Rename FK column and update constraint names
-- =====================================================

ALTER TABLE iroh.contact_persons RENAME COLUMN account_id TO relation_id;

ALTER TABLE iroh.contact_persons
    RENAME CONSTRAINT account_contacts_pk TO contact_persons_pk;

ALTER TABLE iroh.contact_persons
    RENAME CONSTRAINT account_contacts_relation_id_fk TO contact_persons_relation_id_fk;

-- =====================================================
-- 3. Rename indexes
-- =====================================================

DROP INDEX IF EXISTS iroh.account_contacts_account_id_idx;
CREATE INDEX contact_persons_relation_id_idx
    ON iroh.contact_persons(relation_id);

-- =====================================================
-- 4. Drop redundant lead_* columns from iroh.relations
--    These are contact-person fields that now live on contact_persons table
-- =====================================================

ALTER TABLE iroh.relations DROP COLUMN IF EXISTS lead_first_name;
ALTER TABLE iroh.relations DROP COLUMN IF EXISTS lead_last_name;
ALTER TABLE iroh.relations DROP COLUMN IF EXISTS lead_email;
ALTER TABLE iroh.relations DROP COLUMN IF EXISTS lead_phone_number;
ALTER TABLE iroh.relations DROP COLUMN IF EXISTS lead_company_name;
ALTER TABLE iroh.relations DROP COLUMN IF EXISTS lead_company_website;

-- =====================================================
-- 5. Rename remaining lead_* columns to unprefixed names
-- =====================================================

ALTER TABLE iroh.relations RENAME COLUMN lead_industry TO industry;
ALTER TABLE iroh.relations RENAME COLUMN lead_annual_revenue TO annual_revenue;
ALTER TABLE iroh.relations RENAME COLUMN lead_employee_count TO employee_count;
ALTER TABLE iroh.relations RENAME COLUMN lead_first_contact_date TO first_contact_date;
ALTER TABLE iroh.relations RENAME COLUMN lead_last_interaction_date TO last_interaction_date;
ALTER TABLE iroh.relations RENAME COLUMN lead_board_order TO board_order;
ALTER TABLE iroh.relations RENAME COLUMN lead_status TO status;
ALTER TABLE iroh.relations RENAME COLUMN lead_icp_id TO icp_id;
ALTER TABLE iroh.relations RENAME COLUMN lead_presentation_id TO presentation_id;

-- =====================================================
-- 6. Rename constraints on the renamed columns
-- =====================================================

ALTER TABLE iroh.relations
    RENAME CONSTRAINT relations_lead_icp_id_fk TO relations_icp_id_fk;

ALTER TABLE iroh.relations
    RENAME CONSTRAINT relations_lead_presentation_id_fk TO relations_presentation_id_fk;

-- =====================================================
-- 7. Rename/recreate indexes
-- =====================================================

DROP INDEX IF EXISTS iroh.relations_lead_icp_id_idx;
DROP INDEX IF EXISTS iroh.relations_lead_presentation_id_idx;
DROP INDEX IF EXISTS iroh.relations_organization_id_lead_status_idx;

CREATE INDEX relations_icp_id_idx
    ON iroh.relations(icp_id);

CREATE INDEX relations_presentation_id_idx
    ON iroh.relations(presentation_id);

CREATE INDEX relations_organization_id_status_idx
    ON iroh.relations(organization_id, status);
