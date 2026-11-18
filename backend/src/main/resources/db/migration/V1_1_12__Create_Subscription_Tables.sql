-- Korur Database Schema - Create Subscription Tables
-- V1_1_12__Create_Subscription_Tables.sql
--
-- Introduces the subscription domain for tracking software licenses,
-- SaaS subscriptions, and cloud service agreements (ISO 27001 A.5.9).

-- =====================================================
-- 1. Create enum types
-- =====================================================

-- data_classification_enum is created in V1_1_5 (ISMS foundation).

CREATE TYPE korur.subscription_subscription_type_enum AS ENUM (
    'SOFTWARE', 'CLOUD_SERVICE', 'SAAS', 'PLATFORM'
);

CREATE TYPE korur.subscription_subscription_status_enum AS ENUM (
    'ACTIVE', 'EXPIRING', 'EXPIRED', 'CANCELLED'
);

CREATE TYPE korur.subscription_billing_cycle_enum AS ENUM (
    'MONTHLY', 'QUARTERLY', 'YEARLY'
);

-- =====================================================
-- 2. Create subscriptions table
-- =====================================================

CREATE TABLE korur.subscriptions (
    id                  UUID        NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    subscription_type   korur.subscription_subscription_type_enum NOT NULL DEFAULT 'SAAS',
    status              korur.subscription_subscription_status_enum NOT NULL DEFAULT 'ACTIVE',
    classification      korur.data_classification_enum,
    total_seats         INTEGER,
    used_seats          INTEGER     NOT NULL DEFAULT 0,
    cost                NUMERIC(12, 2),
    billing_cycle       korur.subscription_billing_cycle_enum,
    currency            VARCHAR(3),
    start_date          DATE,
    renewal_date        DATE,
    expiry_date         DATE,
    cancelled_at        TIMESTAMP WITH TIME ZONE,
    owner_user_id       UUID,
    supplier_id         UUID,
    contract_reference  VARCHAR(255),
    login_url           VARCHAR(500),
    notes               TEXT,
    organization_id     UUID        NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT subscriptions_pk PRIMARY KEY (id),
    CONSTRAINT subscriptions_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id),
    CONSTRAINT subscriptions_owner_user_id_fk FOREIGN KEY (owner_user_id)
        REFERENCES shrine.users(id),
    CONSTRAINT subscriptions_supplier_id_fk FOREIGN KEY (supplier_id)
        REFERENCES korur.suppliers(id)
);

-- =====================================================
-- 3. Create subscription_seats table
-- =====================================================

CREATE TABLE korur.subscription_seats (
    id              UUID        NOT NULL,
    subscription_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    assigned_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMP WITH TIME ZONE,
    notes           TEXT,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    organization_id UUID        NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT subscription_seats_pk PRIMARY KEY (id),
    CONSTRAINT subscription_seats_subscription_id_fk FOREIGN KEY (subscription_id)
        REFERENCES korur.subscriptions(id) ON DELETE CASCADE,
    CONSTRAINT subscription_seats_organization_id_fk FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id),
    CONSTRAINT subscription_seats_user_id_fk FOREIGN KEY (user_id)
        REFERENCES shrine.users(id)
);

-- =====================================================
-- 4. Create indexes
-- =====================================================

-- Subscriptions indexes
CREATE INDEX subscriptions_organization_id_idx ON korur.subscriptions(organization_id);
CREATE INDEX subscriptions_status_idx ON korur.subscriptions(status);
CREATE INDEX subscriptions_subscription_type_idx ON korur.subscriptions(subscription_type);
CREATE INDEX subscriptions_renewal_date_idx ON korur.subscriptions(renewal_date);
CREATE INDEX subscriptions_owner_user_id_idx ON korur.subscriptions(owner_user_id);
CREATE INDEX subscriptions_supplier_id_idx ON korur.subscriptions(supplier_id);

-- Subscription seats indexes
CREATE INDEX subscription_seats_subscription_id_idx ON korur.subscription_seats(subscription_id);
CREATE INDEX subscription_seats_organization_id_idx ON korur.subscription_seats(organization_id);
CREATE INDEX subscription_seats_user_id_idx ON korur.subscription_seats(user_id);
CREATE INDEX subscription_seats_active_idx ON korur.subscription_seats(subscription_id, user_id)
    WHERE revoked_at IS NULL AND deleted = FALSE;
