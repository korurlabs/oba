-- Village Database Schema - Create Betik Accounting Domain
-- V1_0_3__Create_Betik_Accounting_Domain.sql
--
-- Accounting module for Dutch fiscal administration.
-- Covers the full betik domain: chart of accounts, fiscal periods,
-- invoices, journal entries (dubbel boekhouden), VAT returns,
-- annual returns, import sessions, audit logs, relations,
-- contact persons, articles, quotes, tasks, bank transactions,
-- and email preferences.
-- Uses organization_id FK to shrine.organizations.
--
-- Consolidates: V1_0_4 and V1_0_5 (original numbering)

-- =====================================================
-- 1. Schema
-- =====================================================

CREATE SCHEMA betik;

-- =====================================================
-- 2. Enum types
-- =====================================================

-- Account domain
CREATE TYPE betik_account_type_enum AS ENUM ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE');

CREATE TYPE betik_account_category_enum AS ENUM (
    'FIXED_ASSETS', 'CURRENT_ASSETS', 'CASH', 'RECEIVABLES',
    'LONG_TERM_LIABILITIES', 'SHORT_TERM_LIABILITIES', 'EQUITY_CAPITAL',
    'REVENUE_SALES', 'REVENUE_OTHER',
    'COST_OF_GOODS', 'OPERATING_EXPENSES', 'PERSONNEL', 'DEPRECIATION',
    'FINANCIAL_INCOME', 'FINANCIAL_EXPENSES', 'TAX_EXPENSES'
);

-- Invoice domain
CREATE TYPE betik_invoice_direction_enum AS ENUM ('INCOMING', 'OUTGOING');

CREATE TYPE betik_invoice_status_enum AS ENUM ('DRAFT', 'EXTRACTED', 'REVIEWED', 'BOOKED', 'PAID', 'CANCELLED');

CREATE TYPE betik_vat_rate_type_enum AS ENUM (
    'HIGH_21', 'LOW_9', 'ZERO_0', 'EXEMPT', 'REVERSE_CHARGE',
    'INTRA_COMMUNITY_DELIVERY', 'INTRA_COMMUNITY_ACQUISITION'
);

-- Journal entry domain
CREATE TYPE betik_journal_entry_type_enum AS ENUM (
    'MANUAL', 'INVOICE_BOOKING', 'PAYMENT', 'BANK_IMPORT',
    'ADJUSTMENT', 'OPENING', 'CLOSING'
);

-- VAT return domain
CREATE TYPE betik_vat_return_status_enum AS ENUM ('DRAFT', 'PREPARED', 'SUBMITTED');

-- Annual return domain
CREATE TYPE betik_annual_return_status_enum AS ENUM ('DRAFT', 'PREPARED', 'SUBMITTED');

-- Data import domain
CREATE TYPE betik_import_source_enum AS ENUM ('EXACT_ONLINE', 'SNELSTART', 'CSV', 'MT940');

CREATE TYPE betik_import_status_enum AS ENUM ('UPLOADED', 'PARSED', 'CONFIRMED', 'FAILED');

-- Organization company type (stored on org settings, used for tax calculations)
CREATE TYPE betik_company_type_enum AS ENUM ('ZZP', 'BV', 'VOF', 'EENMANSZAAK', 'STICHTING', 'OTHER');

-- Relations domain
CREATE TYPE betik_relation_type_enum AS ENUM ('CUSTOMER', 'SUPPLIER', 'BOTH');

-- Quotes domain
CREATE TYPE betik_quote_status_enum AS ENUM ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CONVERTED');

-- Tasks domain
CREATE TYPE betik_task_type_enum AS ENUM ('MISSING_RECEIPT', 'MISSING_INVOICE', 'REVIEW_NEEDED', 'VAT_DEADLINE', 'CUSTOM');
CREATE TYPE betik_task_status_enum AS ENUM ('TODO', 'IN_PROGRESS', 'DONE');
CREATE TYPE betik_task_priority_enum AS ENUM ('LOW', 'MEDIUM', 'HIGH');

-- Bank transactions domain
CREATE TYPE betik_match_status_enum AS ENUM ('UNMATCHED', 'MATCHED', 'IGNORED');

-- =====================================================
-- 3. Organization settings (betik-specific config)
-- =====================================================

CREATE TABLE betik.organization_settings (
    id                      UUID NOT NULL,
    organization_id         UUID NOT NULL,
    company_type            betik_company_type_enum NOT NULL DEFAULT 'ZZP',
    kvk_number              VARCHAR(20),
    btw_number              VARCHAR(20),
    iban                    VARCHAR(34),
    fiscal_year_start_month INTEGER NOT NULL DEFAULT 1,
    is_kor_applicable       BOOLEAN NOT NULL DEFAULT FALSE,
    vat_period_type         VARCHAR(10) NOT NULL DEFAULT 'QUARTER',
    next_entry_number       INTEGER NOT NULL DEFAULT 1,
    next_invoice_number     INTEGER NOT NULL DEFAULT 1,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT organization_settings_pk PRIMARY KEY (id),
    CONSTRAINT organization_settings_organization_id_uk UNIQUE (organization_id),
    CONSTRAINT organization_settings_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

-- =====================================================
-- 4. Accounts (Rekeningschema / Chart of Accounts)
-- =====================================================

CREATE TABLE betik.accounts (
    id              UUID NOT NULL,
    organization_id UUID NOT NULL,
    code            VARCHAR(10) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    account_type    betik_account_type_enum NOT NULL,
    category        betik_account_category_enum NOT NULL,
    parent_id       UUID,
    rgs_code        VARCHAR(20),
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT accounts_pk PRIMARY KEY (id),
    CONSTRAINT accounts_organization_id_code_uk UNIQUE (organization_id, code),
    CONSTRAINT accounts_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT accounts_parent_id_fk FOREIGN KEY (parent_id) REFERENCES betik.accounts(id)
);

CREATE INDEX accounts_organization_id_idx ON betik.accounts(organization_id);
CREATE INDEX accounts_parent_id_idx ON betik.accounts(parent_id);
CREATE INDEX accounts_organization_id_account_type_idx ON betik.accounts(organization_id, account_type);

-- =====================================================
-- 5. Fiscal Periods
-- =====================================================

CREATE TABLE betik.fiscal_periods (
    id              UUID NOT NULL,
    organization_id UUID NOT NULL,
    year            INTEGER NOT NULL,
    period          INTEGER NOT NULL,
    period_type     VARCHAR(10) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    is_closed       BOOLEAN NOT NULL DEFAULT FALSE,
    closed_at       TIMESTAMP WITH TIME ZONE,
    closed_by       UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fiscal_periods_pk PRIMARY KEY (id),
    CONSTRAINT fiscal_periods_organization_id_year_period_type_uk UNIQUE (organization_id, year, period, period_type),
    CONSTRAINT fiscal_periods_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX fiscal_periods_organization_id_idx ON betik.fiscal_periods(organization_id);
CREATE INDEX fiscal_periods_organization_id_year_idx ON betik.fiscal_periods(organization_id, year);

-- =====================================================
-- 6. Invoices
-- =====================================================

CREATE TABLE betik.invoices (
    id                       UUID NOT NULL,
    organization_id          UUID NOT NULL,
    invoice_number           VARCHAR(50),
    direction                betik_invoice_direction_enum NOT NULL,
    status                   betik_invoice_status_enum NOT NULL DEFAULT 'DRAFT',
    counterparty_name        VARCHAR(255),
    counterparty_kvk         VARCHAR(20),
    counterparty_btw         VARCHAR(20),
    counterparty_address     TEXT,
    counterparty_iban        VARCHAR(34),
    invoice_date             DATE,
    due_date                 DATE,
    paid_date                DATE,
    total_excl_vat           DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_vat                DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_incl_vat           DECIMAL(12,2) NOT NULL DEFAULT 0,
    extraction_data          JSONB,
    extraction_confidence    DECIMAL(5,2),
    extracted_at             TIMESTAMP WITH TIME ZONE,
    journal_entry_id         UUID,
    original_file_url        VARCHAR(500),
    original_file_name       VARCHAR(255),
    notes                    TEXT,
    is_credit_note           BOOLEAN NOT NULL DEFAULT FALSE,
    related_invoice_id       UUID,
    deleted                  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT invoices_pk PRIMARY KEY (id),
    CONSTRAINT invoices_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT invoices_related_invoice_id_fk FOREIGN KEY (related_invoice_id) REFERENCES betik.invoices(id)
);

CREATE INDEX invoices_organization_id_idx ON betik.invoices(organization_id);
CREATE INDEX invoices_organization_id_status_idx ON betik.invoices(organization_id, status);
CREATE INDEX invoices_organization_id_direction_idx ON betik.invoices(organization_id, direction);
CREATE INDEX invoices_organization_id_invoice_date_idx ON betik.invoices(organization_id, invoice_date);
CREATE INDEX invoices_deleted_idx ON betik.invoices(deleted) WHERE deleted = FALSE;
CREATE INDEX invoices_related_invoice_id_idx ON betik.invoices(related_invoice_id) WHERE related_invoice_id IS NOT NULL;
CREATE UNIQUE INDEX invoices_organization_id_invoice_number_direction_uk ON betik.invoices(organization_id, invoice_number, direction)
    WHERE invoice_number IS NOT NULL AND deleted = FALSE;

-- =====================================================
-- 7. Invoice Line Items
-- =====================================================

CREATE TABLE betik.invoice_line_items (
    id                          UUID NOT NULL,
    invoice_id                  UUID NOT NULL,
    line_number                 INTEGER NOT NULL DEFAULT 1,
    description                 TEXT NOT NULL,
    quantity                    DECIMAL(10,3) NOT NULL DEFAULT 1,
    unit_price                  DECIMAL(12,2) NOT NULL DEFAULT 0,
    vat_rate                    betik_vat_rate_type_enum NOT NULL DEFAULT 'HIGH_21',
    vat_percentage              DECIMAL(5,2) NOT NULL DEFAULT 21.00,
    vat_amount                  DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_excl_vat              DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_incl_vat              DECIMAL(12,2) NOT NULL DEFAULT 0,
    applicable_vat_percentage   DECIMAL(5,2),
    account_id                  UUID,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT invoice_line_items_pk PRIMARY KEY (id),
    CONSTRAINT invoice_line_items_invoice_id_fk FOREIGN KEY (invoice_id) REFERENCES betik.invoices(id) ON DELETE RESTRICT,
    CONSTRAINT invoice_line_items_account_id_fk FOREIGN KEY (account_id) REFERENCES betik.accounts(id)
);

CREATE INDEX invoice_line_items_invoice_id_idx ON betik.invoice_line_items(invoice_id);

-- =====================================================
-- 8. Journal Entries (Journaalposten - Dubbel Boekhouden)
-- =====================================================

CREATE TABLE betik.journal_entries (
    id              UUID NOT NULL,
    organization_id UUID NOT NULL,
    entry_number    INTEGER,
    entry_date      DATE NOT NULL,
    description     TEXT NOT NULL,
    reference       VARCHAR(100),
    entry_type      betik_journal_entry_type_enum NOT NULL DEFAULT 'MANUAL',
    fiscal_period_id UUID,
    invoice_id      UUID,
    is_reversed     BOOLEAN NOT NULL DEFAULT FALSE,
    reversed_by_id  UUID,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT journal_entries_pk PRIMARY KEY (id),
    CONSTRAINT journal_entries_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT journal_entries_fiscal_period_id_fk FOREIGN KEY (fiscal_period_id) REFERENCES betik.fiscal_periods(id),
    CONSTRAINT journal_entries_invoice_id_fk FOREIGN KEY (invoice_id) REFERENCES betik.invoices(id),
    CONSTRAINT journal_entries_reversed_by_id_fk FOREIGN KEY (reversed_by_id) REFERENCES betik.journal_entries(id)
);

CREATE INDEX journal_entries_organization_id_idx ON betik.journal_entries(organization_id);
CREATE INDEX journal_entries_organization_id_entry_date_idx ON betik.journal_entries(organization_id, entry_date);
CREATE INDEX journal_entries_organization_id_entry_type_idx ON betik.journal_entries(organization_id, entry_type);
CREATE INDEX journal_entries_invoice_id_idx ON betik.journal_entries(invoice_id);
CREATE INDEX journal_entries_deleted_idx ON betik.journal_entries(deleted) WHERE deleted = FALSE;
CREATE UNIQUE INDEX journal_entries_invoice_id_uk ON betik.journal_entries(invoice_id)
    WHERE invoice_id IS NOT NULL AND deleted = FALSE;
CREATE UNIQUE INDEX journal_entries_organization_id_entry_number_uk ON betik.journal_entries(organization_id, entry_number)
    WHERE deleted = FALSE;

-- Add FK from invoices.journal_entry_id to journal_entries (circular reference resolved by ordering)
ALTER TABLE betik.invoices ADD CONSTRAINT invoices_journal_entry_id_fk
    FOREIGN KEY (journal_entry_id) REFERENCES betik.journal_entries(id);

-- =====================================================
-- 9. Journal Entry Lines (Boekingsregels - debet/credit)
-- =====================================================

CREATE TABLE betik.journal_entry_lines (
    id                  UUID NOT NULL,
    journal_entry_id    UUID NOT NULL,
    line_number         INTEGER NOT NULL DEFAULT 1,
    account_id          UUID NOT NULL,
    description         TEXT,
    debit               DECIMAL(12,2) NOT NULL DEFAULT 0,
    credit              DECIMAL(12,2) NOT NULL DEFAULT 0,
    vat_rate            betik_vat_rate_type_enum,
    vat_amount          DECIMAL(12,2) DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT journal_entry_lines_pk PRIMARY KEY (id),
    CONSTRAINT journal_entry_lines_debit_ck CHECK (debit >= 0),
    CONSTRAINT journal_entry_lines_credit_ck CHECK (credit >= 0),
    CONSTRAINT journal_entry_lines_journal_entry_id_fk FOREIGN KEY (journal_entry_id) REFERENCES betik.journal_entries(id) ON DELETE RESTRICT,
    CONSTRAINT journal_entry_lines_account_id_fk FOREIGN KEY (account_id) REFERENCES betik.accounts(id)
);

CREATE INDEX journal_entry_lines_journal_entry_id_idx ON betik.journal_entry_lines(journal_entry_id);
CREATE INDEX journal_entry_lines_account_id_idx ON betik.journal_entry_lines(account_id);

-- Deferred constraint trigger: debet must equal credit per journal entry
CREATE OR REPLACE FUNCTION betik.check_journal_entry_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_debit DECIMAL(12,2);
    total_credit DECIMAL(12,2);
BEGIN
    SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
    INTO total_debit, total_credit
    FROM betik.journal_entry_lines
    WHERE journal_entry_id = NEW.journal_entry_id;

    IF total_debit != total_credit THEN
        RAISE EXCEPTION 'Journal entry is niet in balans: debet=% credit=%', total_debit, total_credit;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_check_journal_balance
    AFTER INSERT OR UPDATE ON betik.journal_entry_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION betik.check_journal_entry_balance();

-- =====================================================
-- 10. VAT Returns (BTW-aangifte)
-- =====================================================

CREATE TABLE betik.vat_returns (
    id                      UUID NOT NULL,
    organization_id         UUID NOT NULL,
    fiscal_period_id        UUID,
    period_start            DATE NOT NULL,
    period_end              DATE NOT NULL,
    status                  betik_vat_return_status_enum NOT NULL DEFAULT 'DRAFT',
    rubriek_1a              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_1b              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_1c              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_1d              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_1e              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_2a              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_3a              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_3b              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_3c              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_4a              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_4b              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_5a              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_5b              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_5c              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_5d              DECIMAL(12,2) NOT NULL DEFAULT 0,
    rubriek_5e              DECIMAL(12,2) NOT NULL DEFAULT 0,
    kor_applicable          BOOLEAN NOT NULL DEFAULT FALSE,
    kor_relief_amount       DECIMAL(12,2) DEFAULT 0,
    total_vat_due           DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_vat_deductible    DECIMAL(12,2) NOT NULL DEFAULT 0,
    net_vat_payable         DECIMAL(12,2) NOT NULL DEFAULT 0,
    calculation_details     JSONB,
    submitted_at            TIMESTAMP WITH TIME ZONE,
    submitted_by            UUID,
    notes                   TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT vat_returns_pk PRIMARY KEY (id),
    CONSTRAINT vat_returns_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT vat_returns_fiscal_period_id_fk FOREIGN KEY (fiscal_period_id) REFERENCES betik.fiscal_periods(id)
);

CREATE INDEX vat_returns_organization_id_idx ON betik.vat_returns(organization_id);
CREATE INDEX vat_returns_period_start_idx ON betik.vat_returns(organization_id, period_start);

-- =====================================================
-- 11. Annual Returns (Jaaraangifte)
-- =====================================================

CREATE TABLE betik.annual_returns (
    id                  UUID NOT NULL,
    organization_id     UUID NOT NULL,
    fiscal_year         INTEGER NOT NULL,
    fiscal_period_id    UUID,
    status              betik_annual_return_status_enum NOT NULL DEFAULT 'DRAFT',
    total_revenue       DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_expenses      DECIMAL(14,2) NOT NULL DEFAULT 0,
    net_profit          DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_assets        DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_liabilities   DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_equity        DECIMAL(14,2) NOT NULL DEFAULT 0,
    profit_loss_data    JSONB,
    balance_sheet_data  JSONB,
    submitted_at        TIMESTAMP WITH TIME ZONE,
    submitted_by        UUID,
    notes               TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT annual_returns_pk PRIMARY KEY (id),
    CONSTRAINT annual_returns_organization_id_fiscal_year_uk UNIQUE (organization_id, fiscal_year),
    CONSTRAINT annual_returns_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT annual_returns_fiscal_period_id_fk FOREIGN KEY (fiscal_period_id) REFERENCES betik.fiscal_periods(id)
);

CREATE INDEX annual_returns_organization_id_idx ON betik.annual_returns(organization_id);

-- =====================================================
-- 12. Import Sessions
-- =====================================================

CREATE TABLE betik.import_sessions (
    id                  UUID NOT NULL,
    organization_id     UUID NOT NULL,
    source              betik_import_source_enum NOT NULL,
    status              betik_import_status_enum NOT NULL DEFAULT 'UPLOADED',
    original_file_name  VARCHAR(255),
    original_file_url   VARCHAR(500),
    total_rows          INTEGER DEFAULT 0,
    imported_rows       INTEGER DEFAULT 0,
    failed_rows         INTEGER DEFAULT 0,
    parsed_data         JSONB,
    error_details       JSONB,
    imported_by         UUID,
    confirmed_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT import_sessions_pk PRIMARY KEY (id),
    CONSTRAINT import_sessions_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX import_sessions_organization_id_idx ON betik.import_sessions(organization_id);

-- =====================================================
-- 13. Audit Log (Dutch fiscal compliance: 7 year retention)
-- =====================================================

CREATE TABLE betik.audit_logs (
    id              UUID NOT NULL,
    organization_id UUID NOT NULL,
    user_id         UUID,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    action          VARCHAR(20) NOT NULL,
    old_values      JSONB,
    new_values      JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_logs_pk PRIMARY KEY (id),
    CONSTRAINT audit_logs_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX audit_logs_organization_id_idx ON betik.audit_logs(organization_id);
CREATE INDEX audit_logs_entity_type_entity_id_idx ON betik.audit_logs(entity_type, entity_id);
CREATE INDEX audit_logs_created_at_idx ON betik.audit_logs(created_at);

-- =====================================================
-- 14. Relations
-- =====================================================

CREATE TABLE betik.relations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type betik_relation_type_enum NOT NULL DEFAULT 'CUSTOMER',
    kvk_number VARCHAR(20),
    btw_number VARCHAR(20),
    iban VARCHAR(34),
    street VARCHAR(255),
    house_number VARCHAR(20),
    postal_code VARCHAR(10),
    city VARCHAR(100),
    country VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT relations_pk PRIMARY KEY (id),
    CONSTRAINT relations_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX relations_organization_id_idx ON betik.relations(organization_id);
CREATE INDEX relations_name_idx ON betik.relations(name);

-- =====================================================
-- 15. Contact Persons
-- =====================================================

CREATE TABLE betik.contact_persons (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    relation_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    function_title VARCHAR(100),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT contact_persons_pk PRIMARY KEY (id),
    CONSTRAINT contact_persons_relation_id_fk FOREIGN KEY (relation_id) REFERENCES betik.relations(id) ON DELETE CASCADE
);

CREATE INDEX contact_persons_relation_id_idx ON betik.contact_persons(relation_id);

-- =====================================================
-- 16. Articles
-- =====================================================

CREATE TABLE betik.articles (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0,
    vat_rate betik_vat_rate_type_enum NOT NULL DEFAULT 'HIGH_21',
    unit VARCHAR(50),
    category VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT articles_pk PRIMARY KEY (id),
    CONSTRAINT articles_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT articles_organization_id_code_uk UNIQUE (organization_id, code)
);

CREATE INDEX articles_organization_id_idx ON betik.articles(organization_id);

-- =====================================================
-- 17. Quotes
-- =====================================================

CREATE TABLE betik.quotes (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    quote_number VARCHAR(50) NOT NULL,
    relation_id UUID,
    relation_name VARCHAR(255),
    status betik_quote_status_enum NOT NULL DEFAULT 'DRAFT',
    quote_date DATE NOT NULL,
    valid_until DATE,
    notes TEXT,
    terms_and_conditions TEXT,
    total_excl_vat DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_vat DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_incl_vat DECIMAL(12, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT quotes_pk PRIMARY KEY (id),
    CONSTRAINT quotes_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT quotes_relation_id_fk FOREIGN KEY (relation_id) REFERENCES betik.relations(id) ON DELETE SET NULL
);

CREATE INDEX quotes_organization_id_idx ON betik.quotes(organization_id);

-- =====================================================
-- 18. Quote Line Items
-- =====================================================

CREATE TABLE betik.quote_line_items (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    quote_id UUID NOT NULL,
    article_id UUID,
    description VARCHAR(500) NOT NULL,
    quantity DECIMAL(12, 4) NOT NULL DEFAULT 1,
    unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0,
    vat_rate betik_vat_rate_type_enum NOT NULL DEFAULT 'HIGH_21',
    line_total DECIMAL(12, 2) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT quote_line_items_pk PRIMARY KEY (id),
    CONSTRAINT quote_line_items_quote_id_fk FOREIGN KEY (quote_id) REFERENCES betik.quotes(id) ON DELETE CASCADE,
    CONSTRAINT quote_line_items_article_id_fk FOREIGN KEY (article_id) REFERENCES betik.articles(id) ON DELETE SET NULL
);

CREATE INDEX quote_line_items_quote_id_idx ON betik.quote_line_items(quote_id);

-- =====================================================
-- 19. Tasks
-- =====================================================

CREATE TABLE betik.tasks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    task_type betik_task_type_enum NOT NULL DEFAULT 'CUSTOM',
    status betik_task_status_enum NOT NULL DEFAULT 'TODO',
    priority betik_task_priority_enum NOT NULL DEFAULT 'MEDIUM',
    due_date DATE,
    related_entity_type VARCHAR(50),
    related_entity_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT tasks_pk PRIMARY KEY (id),
    CONSTRAINT tasks_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX tasks_organization_id_idx ON betik.tasks(organization_id);
CREATE INDEX tasks_status_idx ON betik.tasks(status);

-- =====================================================
-- 20. Bank Transactions
-- =====================================================

CREATE TABLE betik.bank_transactions (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id         UUID NOT NULL,
    transaction_date        DATE NOT NULL,
    amount                  DECIMAL(12, 2) NOT NULL,
    counterparty_name       VARCHAR(255),
    counterparty_iban       VARCHAR(34),
    description             TEXT,
    reference               VARCHAR(255),
    matched_invoice_id      UUID,
    match_status            betik_match_status_enum NOT NULL DEFAULT 'UNMATCHED',
    import_session_id       UUID,
    deleted                 BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT bank_transactions_pk PRIMARY KEY (id),
    CONSTRAINT bank_transactions_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT bank_transactions_matched_invoice_id_fk FOREIGN KEY (matched_invoice_id) REFERENCES betik.invoices(id) ON DELETE SET NULL,
    CONSTRAINT bank_transactions_import_session_id_fk FOREIGN KEY (import_session_id) REFERENCES betik.import_sessions(id) ON DELETE SET NULL
);

CREATE INDEX bank_transactions_organization_id_idx ON betik.bank_transactions(organization_id);
CREATE INDEX bank_transactions_match_status_idx ON betik.bank_transactions(match_status);

-- =====================================================
-- 21. Email Preferences
-- =====================================================

CREATE TABLE betik.email_preferences (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    recipients VARCHAR(500),
    days_before INT NOT NULL DEFAULT 14,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT email_preferences_pk PRIMARY KEY (id),
    CONSTRAINT email_preferences_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT email_preferences_organization_id_uk UNIQUE (organization_id)
);
