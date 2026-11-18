-- =================================================================
-- Health Domain: Actuator Health Polling & Kubernetes Metrics Storage
-- =================================================================
-- V1_1_2__Create_Health_Metrics_Tables.sql
--
-- Creates tables for storing periodic snapshots of application health
-- (via actuator), node metrics, and pod metrics from the Kubernetes
-- Metrics API.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE lancer.health_application_health_status_enum AS ENUM ('UP', 'DOWN', 'UNKNOWN', 'DEGRADED');

-- =====================================================
-- 2. Create application_health_snapshots table
-- =====================================================

CREATE TABLE lancer.application_health_snapshots (
    id                UUID NOT NULL,
    service_name      VARCHAR(255) NOT NULL,
    namespace         VARCHAR(255) NOT NULL,
    status            lancer.health_application_health_status_enum NOT NULL,
    response_time_ms  INTEGER,
    details           TEXT,
    content_hash      VARCHAR(64) NOT NULL,
    collected_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT application_health_snapshots_pk PRIMARY KEY (id)
);

-- =====================================================
-- 3. Create node_metrics_snapshots table
-- =====================================================

CREATE TABLE lancer.node_metrics_snapshots (
    id                       UUID NOT NULL,
    node_name                VARCHAR(255) NOT NULL,
    cpu_usage_millicores     BIGINT,
    cpu_capacity_millicores  BIGINT,
    memory_usage_bytes       BIGINT,
    memory_capacity_bytes    BIGINT,
    pod_count                INTEGER,
    collected_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT node_metrics_snapshots_pk PRIMARY KEY (id)
);

-- =====================================================
-- 4. Create pod_metrics_snapshots table
-- =====================================================

CREATE TABLE lancer.pod_metrics_snapshots (
    id                    UUID NOT NULL,
    pod_name              VARCHAR(255) NOT NULL,
    namespace             VARCHAR(255) NOT NULL,
    container_name        VARCHAR(255),
    cpu_usage_millicores  BIGINT,
    memory_usage_bytes    BIGINT,
    collected_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pod_metrics_snapshots_pk PRIMARY KEY (id)
);

-- =====================================================
-- 5. Create indexes
-- =====================================================

-- Application health snapshot indexes
CREATE INDEX application_health_snapshots_service_name_idx ON lancer.application_health_snapshots (service_name);
CREATE INDEX application_health_snapshots_namespace_idx ON lancer.application_health_snapshots (namespace);
CREATE INDEX application_health_snapshots_status_idx ON lancer.application_health_snapshots (status);
CREATE INDEX application_health_snapshots_collected_at_idx ON lancer.application_health_snapshots (collected_at);
CREATE INDEX application_health_snapshots_content_hash_idx ON lancer.application_health_snapshots (content_hash);

-- Node metrics snapshot indexes
CREATE INDEX node_metrics_snapshots_node_name_idx ON lancer.node_metrics_snapshots (node_name);
CREATE INDEX node_metrics_snapshots_collected_at_idx ON lancer.node_metrics_snapshots (collected_at);

-- Pod metrics snapshot indexes
CREATE INDEX pod_metrics_snapshots_pod_name_idx ON lancer.pod_metrics_snapshots (pod_name);
CREATE INDEX pod_metrics_snapshots_namespace_idx ON lancer.pod_metrics_snapshots (namespace);
CREATE INDEX pod_metrics_snapshots_collected_at_idx ON lancer.pod_metrics_snapshots (collected_at);
