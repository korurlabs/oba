-- Village Database Schema - Create TLS Insights Table
-- V1_1_4__Create_Tls_Insights_Table.sql
--
-- This migration creates the tls_insights table in the lancer schema
-- for storing TLS security insights generated during certificate discovery.
-- Certificate entities are stored in Neo4j; this table provides a queryable
-- PostgreSQL side-table for insights only.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE lancer_tls_insight_severity_enum AS ENUM ('INFO', 'WARNING', 'CRITICAL');
CREATE TYPE lancer_tls_insight_type_enum AS ENUM ('MISSING_TLS', 'WEAK_ALGORITHM', 'EXPIRING_SOON', 'EXPIRED', 'SELF_SIGNED');

-- =====================================================
-- 2. Create tls_insights table
-- =====================================================

CREATE TABLE lancer.tls_insights (
    id                  UUID        NOT NULL,
    service_name        VARCHAR(255) NOT NULL,
    namespace           VARCHAR(255) NOT NULL,
    insight_type        lancer_tls_insight_type_enum NOT NULL,
    severity            lancer_tls_insight_severity_enum NOT NULL,
    description         TEXT        NOT NULL,
    affected_resource   VARCHAR(512),
    certificate_node_id BIGINT,
    content_hash        VARCHAR(64) NOT NULL,
    last_seen_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tls_insights_pk PRIMARY KEY (id),
    CONSTRAINT tls_insights_content_hash_uk UNIQUE (content_hash)
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE INDEX tls_insights_severity_idx ON lancer.tls_insights(severity);
CREATE INDEX tls_insights_insight_type_idx ON lancer.tls_insights(insight_type);
CREATE INDEX tls_insights_namespace_idx ON lancer.tls_insights(namespace);
CREATE INDEX tls_insights_content_hash_idx ON lancer.tls_insights(content_hash);
CREATE INDEX tls_insights_last_seen_at_idx ON lancer.tls_insights(last_seen_at);
