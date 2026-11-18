-- Village Database Schema - Iroh Add Vat Return And Ledger And Journal Entry
-- V1_2_6__Iroh_Add_Vat_Return_And_Ledger_And_Journal_Entry.sql
--
-- Adds the BTW-aangifte (Dutch VAT return) foundation and the general-ledger
-- chart-of-accounts + double-entry journal foundation to the Iroh finance domain.
--
-- VAT section:
-- 1) vat_category column on invoice_line_items — fiscal classification of each line.
-- 2) vat_returns — one VAT return per quarter per organisation (snapshot model).
-- 3) vat_return_lines — one row per rubriek per return (frozen at submission).
-- 4) vat_supplements — correction filing referencing an original vat_return.
-- 5) vat_supplement_lines — per-rubriek correction amounts on a supplement.
--
-- Ledger/journal section:
-- 6) ledger_accounts — org-scoped chart of accounts (canonical GL account mapping).
-- 7) journal_entries — journal entry headers (one per accounting event).
-- 8) journal_entry_lines — individual debit/credit lines per journal entry.
--
-- All enums use native PostgreSQL enum types mapped via Hibernate's PostgreSQLEnumJdbcType.
-- Naming convention: iroh_{enum_class_name_snake_case}_enum

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE iroh_vat_category_enum AS ENUM (
    'STANDARD_HIGH',
    'STANDARD_LOW',
    'ZERO_RATE_EXPORT',
    'INTRA_EU_B2B',
    'INTRA_EU_B2C',
    'REVERSE_CHARGE_NL',
    'EXEMPT',
    'NOT_APPLICABLE'
);

CREATE TYPE iroh_vat_rubric_enum AS ENUM (
    'R_1A',
    'R_1B',
    'R_1C',
    'R_1E',
    'R_2A',
    'R_3A',
    'R_3B',
    'R_3C',
    'R_4A',
    'R_4B',
    'R_5B'
);

CREATE TYPE iroh_vat_period_enum AS ENUM ('Q1', 'Q2', 'Q3', 'Q4');

CREATE TYPE iroh_vat_return_status_enum AS ENUM ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED');

CREATE TYPE iroh_vat_supplement_status_enum AS ENUM ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED');

CREATE TYPE iroh_ledger_account_template_enum AS ENUM (
    'BANK',
    'ACCOUNTS_RECEIVABLE',
    'FIXED_ASSETS',
    'VAT_RECEIVABLE',
    'ACCOUNTS_PAYABLE',
    'VAT_PAYABLE',
    'OWNER_EQUITY',
    'CURRENT_YEAR_RESULT',
    'SERVICE_REVENUE',
    'PRODUCT_REVENUE',
    'SOFTWARE_EXPENSE',
    'RENT_EXPENSE',
    'CAR_EXPENSE',
    'DEPRECIATION_EXPENSE'
);

CREATE TYPE iroh_journal_entry_source_type_enum AS ENUM (
    'SALES_INVOICE',
    'PURCHASE_INVOICE',
    'BANK_TRANSACTION',
    'PAYMENT',
    'MANUAL',
    'OPENING_BALANCE',
    'DEPRECIATION',
    'VAT_RETURN',
    'PAYROLL'
);

CREATE TYPE iroh_journal_entry_status_enum AS ENUM ('DRAFT', 'POSTED', 'REVERSED');

-- =====================================================
-- 2. Add vat_category column to invoice_line_items
-- =====================================================

-- Direct NOT NULL — test environment has no existing rows, no backfill needed.
ALTER TABLE iroh.invoice_line_items
    ADD COLUMN vat_category iroh_vat_category_enum NOT NULL;

-- =====================================================
-- 3. Create vat_returns table
-- =====================================================

CREATE TABLE iroh.vat_returns (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    year            INT NOT NULL,
    period          iroh_vat_period_enum NOT NULL,
    status          iroh_vat_return_status_enum NOT NULL,
    submitted_at    TIMESTAMP WITH TIME ZONE,
    total_to_pay    NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT vat_returns_pk PRIMARY KEY (id),
    CONSTRAINT vat_returns_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

-- Partial unique index: one active return per (organisation, year, quarter).
-- Soft-deleted rows are excluded so a re-filed return can reuse the same key.
CREATE UNIQUE INDEX vat_returns_organization_id_year_period_uk
    ON iroh.vat_returns(organization_id, year, period)
    WHERE deleted = FALSE;

CREATE INDEX vat_returns_organization_id_idx ON iroh.vat_returns(organization_id);

-- =====================================================
-- 4. Create vat_return_lines table
-- =====================================================

CREATE TABLE iroh.vat_return_lines (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    vat_return_id   UUID NOT NULL,
    rubriek         iroh_vat_rubric_enum NOT NULL,
    amount_excl_vat NUMERIC(12,2) NOT NULL,
    amount_vat      NUMERIC(12,2) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT vat_return_lines_pk PRIMARY KEY (id),
    CONSTRAINT vat_return_lines_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT vat_return_lines_vat_return_id_fk FOREIGN KEY (vat_return_id) REFERENCES iroh.vat_returns(id) ON DELETE CASCADE
);

-- Partial unique index: each rubric appears at most once per active return.
CREATE UNIQUE INDEX vat_return_lines_vat_return_id_vat_rubric_uk
    ON iroh.vat_return_lines(vat_return_id, rubriek)
    WHERE deleted = FALSE;

CREATE INDEX vat_return_lines_vat_return_id_idx ON iroh.vat_return_lines(vat_return_id);
CREATE INDEX vat_return_lines_organization_id_idx ON iroh.vat_return_lines(organization_id);

-- =====================================================
-- 5. Create vat_supplements table
-- =====================================================

CREATE TABLE iroh.vat_supplements (
    id                     UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id        UUID NOT NULL,
    original_vat_return_id UUID NOT NULL,
    status                 iroh_vat_supplement_status_enum NOT NULL,
    reason                 TEXT NOT NULL,
    submitted_at           TIMESTAMP WITH TIME ZONE,
    total_difference       NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted                BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT vat_supplements_pk PRIMARY KEY (id),
    CONSTRAINT vat_supplements_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT vat_supplements_original_vat_return_id_fk FOREIGN KEY (original_vat_return_id) REFERENCES iroh.vat_returns(id)
);

-- No unique constraint — multiple supplements per return are valid.
CREATE INDEX vat_supplements_original_vat_return_id_idx ON iroh.vat_supplements(original_vat_return_id);
CREATE INDEX vat_supplements_organization_id_idx ON iroh.vat_supplements(organization_id);

-- =====================================================
-- 6. Create vat_supplement_lines table
-- =====================================================

CREATE TABLE iroh.vat_supplement_lines (
    id                        UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id           UUID NOT NULL,
    supplement_id             UUID NOT NULL,
    rubriek                   VARCHAR(8) NOT NULL,
    original_amount_excl_vat  NUMERIC(12,2) NOT NULL,
    original_amount_vat       NUMERIC(12,2) NOT NULL,
    corrected_amount_excl_vat NUMERIC(12,2) NOT NULL,
    corrected_amount_vat      NUMERIC(12,2) NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted                   BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT vat_supplement_lines_pk PRIMARY KEY (id),
    CONSTRAINT vat_supplement_lines_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT vat_supplement_lines_supplement_id_fk FOREIGN KEY (supplement_id) REFERENCES iroh.vat_supplements(id) ON DELETE CASCADE
);

-- Partial unique index: each rubriek appears at most once per active supplement.
CREATE UNIQUE INDEX vat_supplement_lines_supplement_id_rubriek_uk
    ON iroh.vat_supplement_lines(supplement_id, rubriek)
    WHERE deleted = FALSE;

CREATE INDEX vat_supplement_lines_supplement_id_idx ON iroh.vat_supplement_lines(supplement_id);
CREATE INDEX vat_supplement_lines_organization_id_idx ON iroh.vat_supplement_lines(organization_id);

-- =====================================================
-- 7. Create ledger_accounts table
-- =====================================================

CREATE TABLE iroh.ledger_accounts (
    id                  UUID                              NOT NULL DEFAULT gen_random_uuid(),
    organization_id     UUID                              NOT NULL,
    canonical_account   iroh_ledger_account_template_enum NOT NULL,
    code                VARCHAR(20)                       NOT NULL,
    display_name        VARCHAR(120)                      NOT NULL,
    dutch_display_name  VARCHAR(120)                      NOT NULL,
    active              BOOLEAN                           NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE          NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE          NOT NULL DEFAULT NOW(),
    deleted             BOOLEAN                           NOT NULL DEFAULT FALSE,
    CONSTRAINT ledger_accounts_pk PRIMARY KEY (id),
    CONSTRAINT ledger_accounts_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
);

-- Partial unique: one active code per org (soft-deleted rows excluded so a
-- re-created account can reuse the same code within the same org).
CREATE UNIQUE INDEX ledger_accounts_organization_id_code_uk
    ON iroh.ledger_accounts(organization_id, code)
    WHERE deleted = FALSE;

-- Partial unique: one active ledger account per canonical GL account per org.
CREATE UNIQUE INDEX ledger_accounts_organization_id_canonical_account_uk
    ON iroh.ledger_accounts(organization_id, canonical_account)
    WHERE deleted = FALSE;

CREATE INDEX ledger_accounts_organization_id_idx
    ON iroh.ledger_accounts(organization_id);

-- Supports active-filter queries on the chart-of-accounts list screen.
CREATE INDEX ledger_accounts_organization_id_active_idx
    ON iroh.ledger_accounts(organization_id, active);

-- =====================================================
-- 8. Create journal_entries table
-- =====================================================

CREATE TABLE iroh.journal_entries (
    id              UUID                             NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID                             NOT NULL,
    entry_number    VARCHAR(40)                      NOT NULL,
    entry_date      DATE                             NOT NULL,
    description     VARCHAR(500)                     NOT NULL,
    source_type     iroh_journal_entry_source_type_enum NOT NULL,
    source_id       BIGINT,
    status          iroh_journal_entry_status_enum   NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP WITH TIME ZONE         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE         NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN                          NOT NULL DEFAULT FALSE,
    CONSTRAINT journal_entries_pk PRIMARY KEY (id),
    CONSTRAINT journal_entries_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
);

-- Partial unique: entry_number must be unique per org (soft-deleted rows excluded).
CREATE UNIQUE INDEX journal_entries_organization_id_entry_number_uk
    ON iroh.journal_entries(organization_id, entry_number)
    WHERE deleted = FALSE;

CREATE INDEX journal_entries_organization_id_idx
    ON iroh.journal_entries(organization_id);

-- Supports date-range queries on the journal (most recent first is the common case).
CREATE INDEX journal_entries_organization_id_entry_date_idx
    ON iroh.journal_entries(organization_id, entry_date DESC);

-- Supports status-filtered queries (DRAFT vs POSTED list views).
CREATE INDEX journal_entries_organization_id_status_idx
    ON iroh.journal_entries(organization_id, status);

-- Supports source-link queries (find all entries created from a given invoice).
CREATE INDEX journal_entries_source_type_source_id_idx
    ON iroh.journal_entries(source_type, source_id)
    WHERE source_id IS NOT NULL;

-- =====================================================
-- 9. Create journal_entry_lines table
-- =====================================================

CREATE TABLE iroh.journal_entry_lines (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID         NOT NULL,
    journal_entry_id  UUID         NOT NULL,
    ledger_account_id UUID         NOT NULL,
    description       VARCHAR(500),
    debit_amount      NUMERIC(19,2) NOT NULL DEFAULT 0,
    credit_amount     NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT journal_entry_lines_pk PRIMARY KEY (id),
    CONSTRAINT journal_entry_lines_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id),
    CONSTRAINT journal_entry_lines_journal_entry_id_fk FOREIGN KEY (journal_entry_id)
        REFERENCES iroh.journal_entries(id) ON DELETE CASCADE,
    CONSTRAINT journal_entry_lines_ledger_account_id_fk FOREIGN KEY (ledger_account_id)
        REFERENCES iroh.ledger_accounts(id)
);

CREATE INDEX journal_entry_lines_journal_entry_id_idx
    ON iroh.journal_entry_lines(journal_entry_id);

CREATE INDEX journal_entry_lines_organization_id_idx
    ON iroh.journal_entry_lines(organization_id);

-- Supports "which entries reference this account?" look-up.
CREATE INDEX journal_entry_lines_ledger_account_id_idx
    ON iroh.journal_entry_lines(ledger_account_id);
