-- =================================================================
-- Maid Domain: Unused Kubernetes Resources Detection & Cleanup
-- =================================================================

-- Section 1: Create lancer schema if not exists
CREATE SCHEMA IF NOT EXISTS lancer;

-- Section 2: Create unused_resources table
CREATE TABLE lancer.unused_resources (
    id                UUID PRIMARY KEY,
    resource_type     VARCHAR(50)  NOT NULL,
    resource_name     VARCHAR(255) NOT NULL,
    namespace         VARCHAR(255) NOT NULL,
    risk              VARCHAR(20)  NOT NULL,
    reason            TEXT         NOT NULL,
    resource_metadata TEXT,
    content_hash      VARCHAR(64)  NOT NULL,
    last_seen_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    deleted_at        TIMESTAMP WITH TIME ZONE,
    kept_reason       TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_unused_resources_type_name_namespace
        UNIQUE (resource_type, resource_name, namespace)
);

-- Section 3: Create indexes
CREATE INDEX idx_unused_resources_status ON lancer.unused_resources (status);
CREATE INDEX idx_unused_resources_resource_type ON lancer.unused_resources (resource_type);
CREATE INDEX idx_unused_resources_namespace ON lancer.unused_resources (namespace);
CREATE INDEX idx_unused_resources_risk ON lancer.unused_resources (risk);
CREATE INDEX idx_unused_resources_content_hash ON lancer.unused_resources (content_hash);
CREATE INDEX idx_unused_resources_last_seen_at ON lancer.unused_resources (last_seen_at);
