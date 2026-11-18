-- Oba Database Schema - Otak: Create Mail Senders
-- V1_2_112__Otak_Create_Mail_Senders.sql
--
-- Adds the mail senders of an organization to Otak: the mailbox addresses on
-- the Korur tenant that an outreach mail can be sent from. The backend sends
-- with its own application credentials, so no secret is stored here. The
-- display name is kept as Microsoft returns it, and added_by records the user
-- who added the address. added_by has no foreign key on purpose: the row
-- must outlive a removed user, and it is not read through a join.
--
-- No enum type is needed.
--
-- Delete rules:
--   mail_senders_organization_id_fk ON DELETE CASCADE: deleting an
--     organization deletes its mail senders.
--
-- Indexes:
--   mail_senders_organization_id_lower_mail_address_uk is unique per
--     organization on the lower-case mail address, which enforces the rule
--     that an address is added once whatever its case, serves the list of an
--     organization, and serves the organization foreign key and its cascade.

-- =====================================================
-- 1. Create tables
-- =====================================================

CREATE TABLE otak.mail_senders (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    mail_address VARCHAR(254) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    added_by UUID NOT NULL,
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mail_senders_pk PRIMARY KEY (id),
    CONSTRAINT mail_senders_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

CREATE UNIQUE INDEX mail_senders_organization_id_lower_mail_address_uk ON otak.mail_senders USING btree (organization_id, lower((mail_address)::text));
