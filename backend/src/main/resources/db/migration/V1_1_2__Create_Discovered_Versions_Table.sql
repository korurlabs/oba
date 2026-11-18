-- =================================================================
-- Version Domain: Discovered Version Tracking from K8s Cluster
-- =================================================================
-- V1_1_3__Create_Discovered_Versions_Table.sql
--
-- Creates the discovered_versions table for storing application container
-- image versions (from Deployments) and Helm chart release versions
-- (from Helm release secrets) discovered during periodic cluster scans.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE lancer.version_version_type_enum AS ENUM ('APPLICATION', 'HELM_CHART');
CREATE TYPE lancer.version_discovered_version_status_enum AS ENUM ('ACTIVE', 'STALE');

-- =====================================================
-- 2. Create discovered_versions table
-- =====================================================

CREATE TABLE lancer.discovered_versions (
    id               UUID NOT NULL,
    name             VARCHAR(255) NOT NULL,
    namespace        VARCHAR(255) NOT NULL,
    version_type     lancer.version_version_type_enum NOT NULL,
    current_version  VARCHAR(100) NOT NULL,
    chart_name       VARCHAR(255),
    app_version      VARCHAR(100),
    image_name       VARCHAR(500),
    source           VARCHAR(255),
    content_hash     VARCHAR(64) NOT NULL,
    last_seen_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    status           lancer.version_discovered_version_status_enum NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT discovered_versions_pk PRIMARY KEY (id),
    CONSTRAINT discovered_versions_name_namespace_version_type_uk
        UNIQUE (name, namespace, version_type)
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE INDEX discovered_versions_name_idx ON lancer.discovered_versions (name);
CREATE INDEX discovered_versions_namespace_idx ON lancer.discovered_versions (namespace);
CREATE INDEX discovered_versions_version_type_idx ON lancer.discovered_versions (version_type);
CREATE INDEX discovered_versions_status_idx ON lancer.discovered_versions (status);
CREATE INDEX discovered_versions_content_hash_idx ON lancer.discovered_versions (content_hash);
CREATE INDEX discovered_versions_last_seen_at_idx ON lancer.discovered_versions (last_seen_at);
