-- Oba Database Schema - Shrine Create Azure Connections
-- V1_2_142__Shrine_Create_Azure_Connections.sql
--
-- shrine.azure_connections holds an organization's link to an Azure identity
-- (tenant id, and an optional client id) that Oba reaches through a federated credential.
-- No secret is stored: only the identifiers and the subject Oba presents.
-- shrine.azure_connection_subscriptions holds the subscriptions that identity
-- can see, refreshed by a check.
--
-- Delete rules: deleting an organization deletes its connections, and deleting
-- a connection deletes its subscriptions (ON DELETE CASCADE). Deleting the
-- user who created a connection keeps the connection and clears
-- created_by_id (ON DELETE SET NULL).
--
-- Indexes:
--   azure_connections_organization_id_name_uk                one name per organization; serves the organization_id foreign key.
--   azure_connections_organization_id_tenant_id_client_id_uk the same identity is connected once per organization. client_id is optional at create; PostgreSQL's default NULLS DISTINCT lets several connections of one tenant without a client id coexist.
--   azure_connections_subject_uk                             a subject maps to exactly one connection.
--   azure_connections_created_by_id_idx                      foreign key.
--   azure_connection_subscriptions_connection_id_subscription_id_uk
--                                                            a subscription is listed once per connection; serves the connection_id foreign key.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE shrine_azure_connection_status_enum AS ENUM ('PENDING', 'CONNECTED', 'FAILING');

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE shrine.azure_connections (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL,
    client_id UUID,
    subject VARCHAR(600) NOT NULL,
    last_verified_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    status shrine_azure_connection_status_enum NOT NULL DEFAULT 'PENDING',
    organization_id UUID NOT NULL,
    created_by_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT azure_connections_pk PRIMARY KEY (id),
    CONSTRAINT azure_connections_organization_id_name_uk UNIQUE (organization_id, name),
    CONSTRAINT azure_connections_organization_id_tenant_id_client_id_uk UNIQUE (organization_id, tenant_id, client_id),
    CONSTRAINT azure_connections_subject_uk UNIQUE (subject),
    CONSTRAINT azure_connections_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT azure_connections_created_by_id_fk FOREIGN KEY (created_by_id) REFERENCES shrine.users(id) ON DELETE SET NULL
);

CREATE TABLE shrine.azure_connection_subscriptions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    state VARCHAR(32) NOT NULL,
    connection_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT azure_connection_subscriptions_pk PRIMARY KEY (id),
    CONSTRAINT azure_connection_subscriptions_connection_id_subscription_id_uk UNIQUE (connection_id, subscription_id),
    CONSTRAINT azure_connection_subscriptions_connection_id_fk FOREIGN KEY (connection_id) REFERENCES shrine.azure_connections(id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE INDEX azure_connections_created_by_id_idx ON shrine.azure_connections USING btree (created_by_id);
