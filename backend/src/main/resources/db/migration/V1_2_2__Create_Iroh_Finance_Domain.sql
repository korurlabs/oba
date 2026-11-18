-- Village Database Schema - Create Iroh Finance Domain
-- V1_2_2__Create_Iroh_Finance_Domain.sql
--
-- Finance module for Iroh: accounts, account contacts, invoices, and invoice
-- line items. Covers the full iroh domain with organization-scoped data,
-- soft delete support, and explicit FK cascade rules.
-- Uses organization_id FK to shrine.organizations.
--
-- flyway:executeInTransaction=false is required because ALTER TYPE ADD VALUE cannot
-- run inside a transaction. Subsequent statements run in auto-commit mode.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Schema
-- =====================================================

CREATE SCHEMA IF NOT EXISTS iroh;

-- =====================================================
-- 2. Enum types
-- =====================================================

CREATE TYPE iroh_invoice_type_enum AS ENUM ('INVOICE', 'RECEIPT');

CREATE TYPE iroh_invoice_direction_enum AS ENUM ('INCOME', 'EXPENSE');

CREATE TYPE iroh_invoice_status_enum AS ENUM ('DRAFT', 'OPEN', 'PAID', 'CANCELLED');

-- =====================================================
-- 3. Extend existing enum
-- =====================================================

ALTER TYPE shrine_storage_entity_type_enum ADD VALUE IF NOT EXISTS 'INVOICE';

-- =====================================================
-- 4. Accounts
-- =====================================================

CREATE TABLE iroh.accounts (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    vat_number      VARCHAR(20),
    kvk_number      VARCHAR(20),
    email           VARCHAR(255),
    phone           VARCHAR(50),
    website         VARCHAR(500),
    iban            VARCHAR(34),
    notes           TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    address         VARCHAR(500),
    postal_code     VARCHAR(10),
    city            VARCHAR(100),
    country         VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT accounts_pk PRIMARY KEY (id),
    CONSTRAINT accounts_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX accounts_organization_id_idx ON iroh.accounts(organization_id);
CREATE INDEX accounts_organization_id_name_idx ON iroh.accounts(organization_id, name);

-- =====================================================
-- 5. Account Contacts
-- =====================================================

CREATE TABLE iroh.account_contacts (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    account_id      UUID NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    email           VARCHAR(255),
    phone           VARCHAR(50),
    function_title  VARCHAR(100),
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT account_contacts_pk PRIMARY KEY (id),
    CONSTRAINT account_contacts_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT account_contacts_account_id_fk FOREIGN KEY (account_id) REFERENCES iroh.accounts(id) ON DELETE CASCADE
);

CREATE INDEX account_contacts_account_id_idx ON iroh.account_contacts(account_id);

-- =====================================================
-- 6. Invoices
-- =====================================================

CREATE TABLE iroh.invoices (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    account_id      UUID,
    type            iroh_invoice_type_enum NOT NULL DEFAULT 'INVOICE',
    direction       iroh_invoice_direction_enum NOT NULL,
    status          iroh_invoice_status_enum NOT NULL DEFAULT 'DRAFT',
    invoice_number  VARCHAR(50),
    reference       VARCHAR(255),
    invoice_date    DATE,
    due_date        DATE,
    paid_date       DATE,
    total_excl_vat  NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_vat       NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_incl_vat  NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT invoices_pk PRIMARY KEY (id),
    CONSTRAINT invoices_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT invoices_account_id_fk FOREIGN KEY (account_id) REFERENCES iroh.accounts(id) ON DELETE SET NULL
);

CREATE INDEX invoices_organization_id_idx ON iroh.invoices(organization_id);
CREATE INDEX invoices_organization_id_status_idx ON iroh.invoices(organization_id, status);
CREATE INDEX invoices_organization_id_direction_idx ON iroh.invoices(organization_id, direction);
CREATE INDEX invoices_account_id_idx ON iroh.invoices(account_id);

-- =====================================================
-- 7. Invoice Line Items
-- =====================================================

CREATE TABLE iroh.invoice_line_items (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    invoice_id      UUID NOT NULL,
    description     TEXT NOT NULL,
    quantity        NUMERIC(10,3) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(12,2) NOT NULL DEFAULT 0,
    vat_percentage  NUMERIC(5,2) NOT NULL DEFAULT 21.00,
    total_vat       NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_excl_vat  NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_incl_vat  NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT invoice_line_items_pk PRIMARY KEY (id),
    CONSTRAINT invoice_line_items_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT invoice_line_items_invoice_id_fk FOREIGN KEY (invoice_id) REFERENCES iroh.invoices(id) ON DELETE CASCADE
);

CREATE INDEX invoice_line_items_invoice_id_idx ON iroh.invoice_line_items(invoice_id);
