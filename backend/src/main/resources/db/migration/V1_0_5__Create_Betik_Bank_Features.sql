-- Village Database Schema - Create Betik Bank Features
-- V1_0_5__Create_Betik_Bank_Features.sql
--
-- Extends the Betik accounting domain with bank reconciliation capabilities
-- and Bunq API integration.
--
-- Bank Reconciliation: extends bank_transactions with reconciliation fields
-- (suggested matches, booked account, journal entry link, receipt URL, IBAN
-- tracking), a duplicate detection index, CSV profile support on import_sessions,
-- and extended match_status and import_source enums.
--
-- Bunq Integration: persistent storage for Bunq OAuth2 bank connections and
-- their linked monetary accounts. Encrypted token columns are stored as TEXT
-- and decrypted in the application layer via AESEncryptionService (AES-256-GCM).
-- access_token_encrypted is nullable to allow token clearing on disconnect.
--
-- Consolidates: V1_0_6 (Betik_Bank_Reconciliation_Phase1),
--               V1_0_7 (Betik_Bunq_Bank_Connections),
--               V1_0_8 (Betik_Bunq_Allow_Nullable_Tokens) — original numbering

-- =====================================================
-- 1. Extend enums
-- =====================================================

-- New match states: engine-suggested match awaiting user confirmation, and fully booked
ALTER TYPE betik_match_status_enum ADD VALUE IF NOT EXISTS 'SUGGESTED';
ALTER TYPE betik_match_status_enum ADD VALUE IF NOT EXISTS 'BOOKED';

-- Additional import sources: ISO 20022 bank statement format, spreadsheet uploads, and Bunq API
ALTER TYPE betik_import_source_enum ADD VALUE IF NOT EXISTS 'CAMT053';
ALTER TYPE betik_import_source_enum ADD VALUE IF NOT EXISTS 'EXCEL';
ALTER TYPE betik_import_source_enum ADD VALUE IF NOT EXISTS 'BUNQ_API';

-- CSV bank profile enum: identifies layout/column mapping for each Dutch bank's CSV export
CREATE TYPE betik_csv_profile_enum AS ENUM (
    'GENERIC',
    'ING',
    'ABN_AMRO',
    'RABOBANK'
);

-- Connection lifecycle for Bunq OAuth2 connections
CREATE TYPE betik_bunq_connection_status_enum AS ENUM (
    'ACTIVE',
    'DISCONNECTED',
    'ERROR'
);

-- =====================================================
-- 2. Extend betik.bank_transactions with reconciliation columns
-- =====================================================

ALTER TABLE betik.bank_transactions
    ADD COLUMN IF NOT EXISTS suggested_invoice_id UUID,
    ADD COLUMN IF NOT EXISTS match_score           SMALLINT,
    ADD COLUMN IF NOT EXISTS booked_account_id     UUID,
    ADD COLUMN IF NOT EXISTS journal_entry_id      UUID,
    ADD COLUMN IF NOT EXISTS receipt_url           VARCHAR(500),
    ADD COLUMN IF NOT EXISTS bank_account_iban     VARCHAR(34);

-- =====================================================
-- 3. Foreign key constraints on betik.bank_transactions
-- =====================================================

-- Soft link to invoice the engine believes this transaction matches
ALTER TABLE betik.bank_transactions
    ADD CONSTRAINT bank_transactions_suggested_invoice_id_fk
        FOREIGN KEY (suggested_invoice_id)
        REFERENCES betik.invoices(id)
        ON DELETE SET NULL;

-- Chart-of-accounts entry used when the transaction is booked without an invoice
ALTER TABLE betik.bank_transactions
    ADD CONSTRAINT bank_transactions_booked_account_id_fk
        FOREIGN KEY (booked_account_id)
        REFERENCES betik.accounts(id)
        ON DELETE RESTRICT;

-- Journal entry created when this transaction is booked into double-entry accounting
ALTER TABLE betik.bank_transactions
    ADD CONSTRAINT bank_transactions_journal_entry_id_fk
        FOREIGN KEY (journal_entry_id)
        REFERENCES betik.journal_entries(id)
        ON DELETE RESTRICT;

-- =====================================================
-- 4. Performance indexes on betik.bank_transactions
-- =====================================================

-- Composite index covering the primary reconciliation list query (filter by org + status)
CREATE INDEX IF NOT EXISTS bank_transactions_organization_id_match_status_idx
    ON betik.bank_transactions(organization_id, match_status);

-- Date-range queries per organisation (statement view, period filters)
CREATE INDEX IF NOT EXISTS bank_transactions_organization_id_transaction_date_idx
    ON betik.bank_transactions(organization_id, transaction_date);

-- Partial index on FK — only rows that actually have a suggestion need fast lookups
CREATE INDEX IF NOT EXISTS bank_transactions_suggested_invoice_id_idx
    ON betik.bank_transactions(suggested_invoice_id)
    WHERE suggested_invoice_id IS NOT NULL;

-- Index new FK columns so joins and ON DELETE scans are fast
CREATE INDEX IF NOT EXISTS bank_transactions_booked_account_id_idx
    ON betik.bank_transactions(booked_account_id)
    WHERE booked_account_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS bank_transactions_journal_entry_id_idx
    ON betik.bank_transactions(journal_entry_id)
    WHERE journal_entry_id IS NOT NULL;

-- =====================================================
-- 5. Duplicate detection index on betik.bank_transactions
-- =====================================================

-- Prevents re-importing the same transaction: only applies to rows with both IBAN and
-- reference present (rows missing either field cannot be reliably deduplicated this way).
CREATE UNIQUE INDEX IF NOT EXISTS bank_transactions_dedupe_uk
    ON betik.bank_transactions(organization_id, transaction_date, amount, counterparty_iban, reference)
    WHERE deleted = FALSE
      AND counterparty_iban IS NOT NULL
      AND reference IS NOT NULL;

-- =====================================================
-- 6. CSV profile column on betik.import_sessions
-- =====================================================

-- Tracks which bank's CSV layout was used so the parser can apply the correct field mapping
ALTER TABLE betik.import_sessions
    ADD COLUMN IF NOT EXISTS csv_profile betik_csv_profile_enum;

-- =====================================================
-- 7. bunq_bank_connections table
-- =====================================================

-- One row per organisation that has completed the Bunq OAuth2 flow.
-- The unique partial index enforces at most one active connection per org.
-- access_token_encrypted is nullable: tokens are cleared to NULL on disconnect
-- rather than storing an encrypted placeholder.
CREATE TABLE betik.bunq_bank_connections (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    organization_id         UUID        NOT NULL,
    bunq_user_id            VARCHAR(64),
    access_token_encrypted  TEXT,
    refresh_token_encrypted TEXT,
    token_expires_at        TIMESTAMPTZ,
    installation_token      TEXT,
    status                  betik_bunq_connection_status_enum NOT NULL DEFAULT 'ACTIVE',
    last_sync_at            TIMESTAMPTZ,
    sync_cursor             VARCHAR(255),
    error_message           TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted                 BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT bunq_bank_connections_pk PRIMARY KEY (id),
    CONSTRAINT bunq_bank_connections_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

-- =====================================================
-- 8. bunq_monetary_accounts table
-- =====================================================

-- Bunq accounts discovered for the linked user; one connection can have
-- multiple IBANs (e.g., main account + savings pockets).
-- bunq_account_id is Bunq's internal integer identifier — kept as BIGINT
-- because Bunq IDs exceed INT range for high-volume accounts.
CREATE TABLE betik.bunq_monetary_accounts (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    connection_id    UUID        NOT NULL,
    bunq_account_id  BIGINT      NOT NULL,
    iban             VARCHAR(34) NOT NULL,
    description      VARCHAR(255),
    account_type     VARCHAR(50),
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT bunq_monetary_accounts_pk PRIMARY KEY (id),
    CONSTRAINT bunq_monetary_accounts_connection_id_fk
        FOREIGN KEY (connection_id)
        REFERENCES betik.bunq_bank_connections(id)
        ON DELETE CASCADE
);

-- =====================================================
-- 9. Indexes — bunq_bank_connections
-- =====================================================

-- organization_id lookup: primary query path (get connection for current org)
CREATE INDEX bunq_bank_connections_organization_id_idx
    ON betik.bunq_bank_connections(organization_id);

-- Enforce exactly one non-deleted connection per organisation.
CREATE UNIQUE INDEX bunq_bank_connections_organization_id_uk
    ON betik.bunq_bank_connections(organization_id)
    WHERE deleted = FALSE;

-- Status index: scheduler queries WHERE status = 'ACTIVE' for sync runs
CREATE INDEX bunq_bank_connections_status_idx
    ON betik.bunq_bank_connections(status)
    WHERE deleted = FALSE;

-- =====================================================
-- 10. Indexes — bunq_monetary_accounts
-- =====================================================

-- FK index: cascade delete scan and per-connection account list queries
CREATE INDEX bunq_monetary_accounts_connection_id_idx
    ON betik.bunq_monetary_accounts(connection_id);

-- Prevent duplicate account rows for the same (connection, Bunq account ID) pair.
CREATE UNIQUE INDEX bunq_monetary_accounts_connection_id_bunq_account_id_uk
    ON betik.bunq_monetary_accounts(connection_id, bunq_account_id)
    WHERE deleted = FALSE;
