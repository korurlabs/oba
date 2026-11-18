-- Oba Database Schema - Otak: Add Contact Kind
-- V1_2_120__Otak_Add_Contact_Kind.sql
--
-- A contact gets a kind: PERSON (a person at the relation) or BUSINESS (the
-- company's general details, such as info@ or sales@). The kind is independent
-- of email_type: nothing in the database ties the two together.
--
-- The main contact of V1_2_117 becomes the main person contact (main_contact is
-- renamed main_person_contact) and a relation gets a main business contact
-- (main_business_contact) under the same rule: at most one per relation, kept
-- by the application and refused by the database when a second one is written.
--
-- Backfill: the kind column defaults to PERSON, which fills every existing
-- row, so every existing contact is a PERSON and keeps its main flag as main
-- person contact. There is no business backfill: no BUSINESS contact exists
-- yet, so no row can be a main business contact, and members reclassify
-- contacts by hand.
--
-- Constraints:
--   contacts_main_person_contact_ck allows the main person flag only on a
--     PERSON, and contacts_main_business_contact_ck allows the main business
--     flag only on a BUSINESS, so a contact never carries both flags.
--
-- Delete rules: none change. The new columns are not keys, and
-- contacts_relation_id_organization_id_fk still deletes the contacts with
-- their relation.
--
-- Indexes:
--   contacts_relation_id_main_person_contact_uk is the index of V1_2_117 under
--     its new name. Its definition (partial unique on relation_id where the
--     flag is true) follows the renamed column by itself.
--   contacts_relation_id_main_business_contact_uk is a partial unique index on
--     relation_id where main_business_contact is true. It refuses a second main
--     business contact on one relation and serves the lookup of the main
--     business contact of a relation and of a page of relations. Contacts that
--     are not main are not indexed, so the index stays small.
--
-- The enum type is created in the public schema with an otak_ prefix, as in
-- V1_2_116. The value order matches the Java enum ContactKind.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE otak_contact_kind_enum AS ENUM ('PERSON', 'BUSINESS');

-- =====================================================
-- 2. Add the kind column (the default is the backfill)
-- =====================================================

ALTER TABLE otak.contacts ADD COLUMN kind otak_contact_kind_enum NOT NULL DEFAULT 'PERSON';

-- =====================================================
-- 3. Rename the main contact to main person contact
-- =====================================================

ALTER TABLE otak.contacts RENAME COLUMN main_contact TO main_person_contact;
ALTER INDEX otak.contacts_relation_id_main_contact_uk RENAME TO contacts_relation_id_main_person_contact_uk;

-- =====================================================
-- 4. Add the main business contact
-- =====================================================

ALTER TABLE otak.contacts ADD COLUMN main_business_contact BOOLEAN NOT NULL DEFAULT false;

-- =====================================================
-- 5. Create constraints
-- =====================================================

ALTER TABLE otak.contacts ADD CONSTRAINT contacts_main_person_contact_ck
    CHECK (NOT main_person_contact OR kind = 'PERSON');
ALTER TABLE otak.contacts ADD CONSTRAINT contacts_main_business_contact_ck
    CHECK (NOT main_business_contact OR kind = 'BUSINESS');

-- =====================================================
-- 6. Create indexes
-- =====================================================

CREATE UNIQUE INDEX contacts_relation_id_main_business_contact_uk ON otak.contacts USING btree (relation_id) WHERE main_business_contact;
