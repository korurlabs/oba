-- Village Database Schema - Iroh Add Kraken Integration
-- V1_2_32__Iroh_Add_Kraken_Integration.sql
--
-- Introduces the Kraken crypto exchange integration domain to the Iroh finance module.
-- This migration:
--   1. Extends existing ENUMs: iroh_journal_entry_source_type_enum, iroh_ledger_account_template_enum
--   2. Creates new PostgreSQL ENUMs: iroh_kraken_trade_type_enum, iroh_kraken_order_type_enum,
--      iroh_kraken_trade_status_enum
--   3. Creates tables (in FK dependency order):
--        iroh.kraken_connections       — encrypted API credentials per organisation
--        iroh.kraken_balances          — snapshot of held crypto balances (upserted per poll cycle)
--        iroh.kraken_trades            — trade history + GL journal entry link
--        iroh.kraken_staking_rewards   — staking ledger entries + GL journal entry link
--        iroh.kraken_market_prices     — spot price snapshot per trading pair per org (upserted)
--   4. Indexes all foreign keys immediately after table creation (per flyway-migration-convention).
--   5. Adds a partial unique index on kraken_connections to enforce one active connection per org.
--
-- Soft-delete follows the project convention: `deleted BOOLEAN NOT NULL DEFAULT FALSE`.
-- flyway:executeInTransaction=false is required because ALTER TYPE ... ADD VALUE and
-- CREATE TYPE cannot run inside a transaction in all PostgreSQL versions.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Extend existing ENUMs
-- =====================================================

ALTER TYPE iroh_journal_entry_source_type_enum ADD VALUE IF NOT EXISTS 'CRYPTO_TRADE';
ALTER TYPE iroh_journal_entry_source_type_enum ADD VALUE IF NOT EXISTS 'CRYPTO_STAKING';

ALTER TYPE iroh_ledger_account_template_enum ADD VALUE IF NOT EXISTS 'CRYPTO_ASSET';
ALTER TYPE iroh_ledger_account_template_enum ADD VALUE IF NOT EXISTS 'CRYPTO_STAKING_INCOME';
ALTER TYPE iroh_ledger_account_template_enum ADD VALUE IF NOT EXISTS 'CRYPTO_TRADE_FEE_EXPENSE';
ALTER TYPE iroh_ledger_account_template_enum ADD VALUE IF NOT EXISTS 'CRYPTO_GAIN_LOSS';

-- =====================================================
-- 2. Create new ENUMs
-- =====================================================

CREATE TYPE iroh_kraken_trade_type_enum AS ENUM (
    'BUY',
    'SELL'
);

CREATE TYPE iroh_kraken_order_type_enum AS ENUM (
    'MARKET',
    'LIMIT',
    'STOP_LOSS',
    'TAKE_PROFIT'
);

CREATE TYPE iroh_kraken_trade_status_enum AS ENUM (
    'PENDING',
    'CLOSED',
    'CANCELED'
);

-- =====================================================
-- 3. Create iroh.kraken_connections
-- =====================================================

-- Stores one encrypted Kraken API credential set per organisation.
-- Only one active (non-deleted) connection is allowed per org — enforced by
-- the partial unique index at the bottom of this section.

CREATE TABLE iroh.kraken_connections (
    id                   UUID                     NOT NULL DEFAULT gen_random_uuid(),
    api_key              VARCHAR(200)             NOT NULL,
    api_secret_enc       TEXT                     NOT NULL,
    label                VARCHAR(100),
    active               BOOLEAN                  NOT NULL DEFAULT TRUE,
    last_polled_at       TIMESTAMP WITH TIME ZONE,
    organization_id      UUID                     NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted              BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT kraken_connections_pk PRIMARY KEY (id),
    CONSTRAINT kraken_connections_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id)
);

CREATE INDEX kraken_connections_organization_id_idx
    ON iroh.kraken_connections(organization_id);

-- One active connection per org: prevents a second simultaneous connection.
-- Partial index ignores soft-deleted rows so a re-connect after disconnect works.
CREATE UNIQUE INDEX kraken_connections_organization_id_active_uk
    ON iroh.kraken_connections(organization_id)
    WHERE active = TRUE AND deleted = FALSE;

-- =====================================================
-- 4. Create iroh.kraken_balances
-- =====================================================

-- Snapshot of Kraken account balances, upserted on every private polling cycle.
-- One row per asset per organisation (upserted on conflict of the unique constraint).

CREATE TABLE iroh.kraken_balances (
    id                     UUID                     NOT NULL DEFAULT gen_random_uuid(),
    asset                  VARCHAR(20)              NOT NULL,
    quantity               NUMERIC(30, 10)          NOT NULL,
    eur_price              NUMERIC(20, 8),
    eur_value              NUMERIC(20, 2),
    snapshot_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    organization_id        UUID                     NOT NULL,
    kraken_connection_id   UUID                     NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted                BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT kraken_balances_pk PRIMARY KEY (id),
    CONSTRAINT kraken_balances_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT kraken_balances_kraken_connection_id_fk
        FOREIGN KEY (kraken_connection_id) REFERENCES iroh.kraken_connections(id),
    CONSTRAINT kraken_balances_organization_id_asset_uk
        UNIQUE (organization_id, asset)
);

CREATE INDEX kraken_balances_organization_id_idx
    ON iroh.kraken_balances(organization_id);

CREATE INDEX kraken_balances_kraken_connection_id_idx
    ON iroh.kraken_balances(kraken_connection_id);

-- =====================================================
-- 5. Create iroh.kraken_trades
-- =====================================================

-- Records each trade imported from Kraken and any order placed via Iroh.
-- Linked to a journal entry once the GL posting is made.
-- UNIQUE (organization_id, kraken_txid) prevents duplicate imports.

CREATE TABLE iroh.kraken_trades (
    id                     UUID                          NOT NULL DEFAULT gen_random_uuid(),
    kraken_txid            VARCHAR(100)                  NOT NULL,
    pair                   VARCHAR(20)                   NOT NULL,
    base_asset             VARCHAR(20)                   NOT NULL,
    quote_asset            VARCHAR(20)                   NOT NULL,
    trade_type             iroh_kraken_trade_type_enum   NOT NULL,
    order_type             iroh_kraken_order_type_enum   NOT NULL,
    volume                 NUMERIC(30, 10)               NOT NULL,
    price                  NUMERIC(20, 8)                NOT NULL,
    cost                   NUMERIC(20, 8)                NOT NULL,
    fee                    NUMERIC(20, 8)                NOT NULL,
    eur_cost               NUMERIC(20, 2),
    trade_status           iroh_kraken_trade_status_enum NOT NULL,
    trade_date             DATE                          NOT NULL,
    journal_entry_id       UUID,
    kraken_order_id        VARCHAR(100),
    organization_id        UUID                          NOT NULL,
    kraken_connection_id   UUID                          NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE      NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE      NOT NULL DEFAULT NOW(),
    deleted                BOOLEAN                       NOT NULL DEFAULT FALSE,
    CONSTRAINT kraken_trades_pk PRIMARY KEY (id),
    CONSTRAINT kraken_trades_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT kraken_trades_kraken_connection_id_fk
        FOREIGN KEY (kraken_connection_id) REFERENCES iroh.kraken_connections(id),
    CONSTRAINT kraken_trades_journal_entry_id_fk
        FOREIGN KEY (journal_entry_id) REFERENCES iroh.journal_entries(id),
    CONSTRAINT kraken_trades_organization_id_kraken_txid_uk
        UNIQUE (organization_id, kraken_txid)
);

CREATE INDEX kraken_trades_organization_id_idx
    ON iroh.kraken_trades(organization_id);

CREATE INDEX kraken_trades_kraken_connection_id_idx
    ON iroh.kraken_trades(kraken_connection_id);

CREATE INDEX kraken_trades_journal_entry_id_idx
    ON iroh.kraken_trades(journal_entry_id);

-- Queried frequently for status-based reconciliation during polling cycles.
CREATE INDEX kraken_trades_trade_status_idx
    ON iroh.kraken_trades(trade_status);

-- Queried for audit trail and duplicate-import guard by Kraken transaction id.
CREATE INDEX kraken_trades_kraken_txid_idx
    ON iroh.kraken_trades(kraken_txid);

-- =====================================================
-- 6. Create iroh.kraken_staking_rewards
-- =====================================================

-- Records each staking ledger entry imported from Kraken.
-- UNIQUE (organization_id, kraken_ledger_id) prevents duplicate imports.

CREATE TABLE iroh.kraken_staking_rewards (
    id                     UUID                     NOT NULL DEFAULT gen_random_uuid(),
    kraken_ledger_id       VARCHAR(100)             NOT NULL,
    asset                  VARCHAR(20)              NOT NULL,
    amount                 NUMERIC(30, 10)          NOT NULL,
    eur_value              NUMERIC(20, 2),
    reward_date            DATE                     NOT NULL,
    journal_entry_id       UUID,
    organization_id        UUID                     NOT NULL,
    kraken_connection_id   UUID                     NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted                BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT kraken_staking_rewards_pk PRIMARY KEY (id),
    CONSTRAINT kraken_staking_rewards_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT kraken_staking_rewards_kraken_connection_id_fk
        FOREIGN KEY (kraken_connection_id) REFERENCES iroh.kraken_connections(id),
    CONSTRAINT kraken_staking_rewards_journal_entry_id_fk
        FOREIGN KEY (journal_entry_id) REFERENCES iroh.journal_entries(id),
    CONSTRAINT kraken_staking_rewards_organization_id_kraken_ledger_id_uk
        UNIQUE (organization_id, kraken_ledger_id)
);

CREATE INDEX kraken_staking_rewards_organization_id_idx
    ON iroh.kraken_staking_rewards(organization_id);

CREATE INDEX kraken_staking_rewards_kraken_connection_id_idx
    ON iroh.kraken_staking_rewards(kraken_connection_id);

-- Queried for duplicate-import guard by Kraken ledger id.
CREATE INDEX kraken_staking_rewards_kraken_ledger_id_idx
    ON iroh.kraken_staking_rewards(kraken_ledger_id);

-- =====================================================
-- 7. Create iroh.kraken_market_prices
-- =====================================================

-- Snapshot of public Kraken ticker data per trading pair per organisation.
-- Upserted on every market-data polling cycle.
-- UNIQUE (organization_id, pair) enforces one current price row per pair per org.

CREATE TABLE iroh.kraken_market_prices (
    id                  UUID                     NOT NULL DEFAULT gen_random_uuid(),
    pair                VARCHAR(20)              NOT NULL,
    base_asset          VARCHAR(20)              NOT NULL,
    quote_asset         VARCHAR(20)              NOT NULL,
    last_price          NUMERIC(20, 8)           NOT NULL,
    ask_price           NUMERIC(20, 8),
    bid_price           NUMERIC(20, 8),
    volume_24h          NUMERIC(30, 10),
    price_change_24h    NUMERIC(10, 4),
    snapshot_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    organization_id     UUID                     NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted             BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT kraken_market_prices_pk PRIMARY KEY (id),
    CONSTRAINT kraken_market_prices_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT kraken_market_prices_organization_id_pair_uk
        UNIQUE (organization_id, pair)
);

CREATE INDEX kraken_market_prices_organization_id_idx
    ON iroh.kraken_market_prices(organization_id);

-- Queried when the market-data scheduler builds the EUR pair list for portfolio valuation.
CREATE INDEX kraken_market_prices_pair_idx
    ON iroh.kraken_market_prices(pair);
