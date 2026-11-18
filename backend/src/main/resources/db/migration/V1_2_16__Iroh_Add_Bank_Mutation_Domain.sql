-- Village Database Schema - Add Iroh Bank Mutation Domain
-- V1_2_14__Iroh_Add_Bank_Mutation_Domain.sql
--
-- Adds the bank mutation core domain: bank accounts, bank mutations, and invoice payment
-- allocations. This is the foundational layer for importing transactions from external banks
-- (bunq, Rabobank, ING, ...) and matching them to invoices for accounting.
--
-- All tables live in the existing iroh schema and reuse the project conventions:
--  - UUID primary keys with gen_random_uuid()
--  - TIMESTAMP WITH TIME ZONE for created_at / updated_at, defaulted to NOW()
--  - deleted BOOLEAN soft-delete column matching SoftDeleteBaseEntity
--  - organization_id FK to shrine.organizations
--  - schema-level uniqueness for IBAN, (provider, externalId), and (organization, externalId)

-- =====================================================
-- 1. Bank Accounts
-- =====================================================

CREATE TABLE iroh.bank_accounts (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL,
    ledger_account_id UUID NOT NULL,
    account_name      VARCHAR(100) NOT NULL,
    iban              VARCHAR(34) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    external_id       VARCHAR(100) NOT NULL,
    external_provider VARCHAR(50) NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT bank_accounts_pk PRIMARY KEY (id),
    CONSTRAINT bank_accounts_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT bank_accounts_ledger_account_id_fk
        FOREIGN KEY (ledger_account_id) REFERENCES iroh.ledger_accounts(id)
);

CREATE INDEX bank_accounts_organization_id_idx
    ON iroh.bank_accounts(organization_id);
CREATE INDEX bank_accounts_ledger_account_id_idx
    ON iroh.bank_accounts(ledger_account_id);
CREATE INDEX bank_accounts_organization_id_active_idx
    ON iroh.bank_accounts(organization_id, active);

-- IBAN is globally unique across non-deleted accounts. Partial unique index lets a deleted
-- account share an IBAN with a freshly registered one.
CREATE UNIQUE INDEX bank_accounts_iban_uidx
    ON iroh.bank_accounts(iban)
    WHERE deleted = FALSE;

-- (provider, external_id) is unique across non-deleted accounts: one bunq account id can only
-- back one active row at a time.
CREATE UNIQUE INDEX bank_accounts_provider_external_id_uidx
    ON iroh.bank_accounts(external_provider, external_id)
    WHERE deleted = FALSE;

-- =====================================================
-- 2. Bank Mutations
-- =====================================================

CREATE TABLE iroh.bank_mutations (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL,
    bank_account_id   UUID NOT NULL,
    external_id       VARCHAR(100) NOT NULL,
    booking_date      DATE NOT NULL,
    amount            NUMERIC(19,4) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    counterparty_name VARCHAR(255) NOT NULL,
    counterparty_iban VARCHAR(34),
    description       TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT bank_mutations_pk PRIMARY KEY (id),
    CONSTRAINT bank_mutations_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT bank_mutations_bank_account_id_fk
        FOREIGN KEY (bank_account_id) REFERENCES iroh.bank_accounts(id),
    CONSTRAINT bank_mutations_status_chk
        CHECK (status IN ('UNMATCHED', 'PROPOSED', 'MATCHED', 'IGNORED'))
);

CREATE INDEX bank_mutations_organization_id_idx
    ON iroh.bank_mutations(organization_id);
CREATE INDEX bank_mutations_bank_account_id_idx
    ON iroh.bank_mutations(bank_account_id);
CREATE INDEX bank_mutations_organization_id_status_idx
    ON iroh.bank_mutations(organization_id, status);
CREATE INDEX bank_mutations_organization_id_booking_date_idx
    ON iroh.bank_mutations(organization_id, booking_date DESC);
CREATE INDEX bank_mutations_bank_account_id_booking_date_idx
    ON iroh.bank_mutations(bank_account_id, booking_date DESC);

-- (organization, external_id) unique across non-deleted mutations: prevents re-importing the
-- same bunq mutation id twice for the same org.
CREATE UNIQUE INDEX bank_mutations_org_external_id_uidx
    ON iroh.bank_mutations(organization_id, external_id)
    WHERE deleted = FALSE;

-- =====================================================
-- 3. Invoice Payment Allocations
-- =====================================================

CREATE TABLE iroh.invoice_payment_allocations (
    id               UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL,
    invoice_id       UUID NOT NULL,
    bank_mutation_id UUID NOT NULL,
    amount           NUMERIC(19,4) NOT NULL,
    allocation_date  DATE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT invoice_payment_allocations_pk PRIMARY KEY (id),
    CONSTRAINT invoice_payment_allocations_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT invoice_payment_allocations_invoice_id_fk
        FOREIGN KEY (invoice_id) REFERENCES iroh.invoices(id),
    CONSTRAINT invoice_payment_allocations_bank_mutation_id_fk
        FOREIGN KEY (bank_mutation_id) REFERENCES iroh.bank_mutations(id)
);

CREATE INDEX invoice_payment_allocations_organization_id_idx
    ON iroh.invoice_payment_allocations(organization_id);
CREATE INDEX invoice_payment_allocations_invoice_id_idx
    ON iroh.invoice_payment_allocations(invoice_id);
CREATE INDEX invoice_payment_allocations_bank_mutation_id_idx
    ON iroh.invoice_payment_allocations(bank_mutation_id);
CREATE INDEX invoice_payment_allocations_organization_id_invoice_id_idx
    ON iroh.invoice_payment_allocations(organization_id, invoice_id);
CREATE INDEX invoice_payment_allocations_organization_id_mutation_id_idx
    ON iroh.invoice_payment_allocations(organization_id, bank_mutation_id);
