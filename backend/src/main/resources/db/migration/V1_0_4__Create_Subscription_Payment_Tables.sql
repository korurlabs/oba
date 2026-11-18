-- Village Database Schema - Create Subscription Payment Tables
-- V1_0_4__Create_Subscription_Payment_Tables.sql
--
-- Subscription and Payment tables for Mollie paywall integration.
-- Tracks one subscription per user per organization and individual Mollie payment transactions.

-- =====================================================
-- 1. Enum types
-- =====================================================

CREATE TYPE shrine_subscription_status_enum AS ENUM (
    'TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED'
);

CREATE TYPE shrine_subscription_plan_enum AS ENUM (
    'FREE_TRIAL', 'MONTHLY', 'YEARLY'
);

CREATE TYPE shrine_subscription_payment_status_enum AS ENUM (
    'CREATED', 'PENDING', 'PAID', 'FAILED', 'CANCELLED'
);

-- =====================================================
-- 2. Tables
-- =====================================================

-- Subscriptions table — one per user per organization
CREATE TABLE shrine.subscriptions (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id                 UUID            NOT NULL,
    organization_id         UUID            NOT NULL,
    status                  shrine_subscription_status_enum NOT NULL DEFAULT 'TRIAL',
    plan                    shrine_subscription_plan_enum   NOT NULL,
    mollie_customer_id      VARCHAR(255),
    mollie_subscription_id  VARCHAR(255),
    start_date              TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date                TIMESTAMP WITH TIME ZONE,
    cancelled_at            TIMESTAMP WITH TIME ZONE,
    trial_ends_at           TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT subscriptions_pk PRIMARY KEY (id),
    CONSTRAINT subscriptions_user_id_organization_id_uk UNIQUE (user_id, organization_id),
    CONSTRAINT subscriptions_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id) ON DELETE CASCADE,
    CONSTRAINT subscriptions_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

-- Subscription payments table — tracks each Mollie payment transaction
CREATE TABLE shrine.subscription_payments (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    mollie_payment_id   VARCHAR(255)    NOT NULL,
    amount              NUMERIC(10, 2)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'EUR',
    payment_method      VARCHAR(255),
    description         VARCHAR(255),
    mollie_status       VARCHAR(50),
    status              shrine_subscription_payment_status_enum NOT NULL DEFAULT 'CREATED',
    paid_at             TIMESTAMP WITH TIME ZONE,
    failed_at           TIMESTAMP WITH TIME ZONE,
    cancelled_at        TIMESTAMP WITH TIME ZONE,
    subscription_id     UUID            NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT subscription_payments_pk PRIMARY KEY (id),
    CONSTRAINT subscription_payments_subscription_id_fk
        FOREIGN KEY (subscription_id) REFERENCES shrine.subscriptions(id)
);

-- =====================================================
-- 3. Indexes
-- =====================================================

CREATE INDEX subscriptions_user_id_organization_id_idx ON shrine.subscriptions(user_id, organization_id);
CREATE INDEX subscriptions_status_idx ON shrine.subscriptions(status);
CREATE INDEX subscription_payments_mollie_payment_id_idx ON shrine.subscription_payments(mollie_payment_id);
CREATE INDEX subscription_payments_subscription_id_idx ON shrine.subscription_payments(subscription_id);
