-- =================================================================
-- Integrity Domain: Discovered Dead Events Table
-- =================================================================
-- Stores dead letter events discovered from in-cluster applications
-- via periodic scanning of /v1/outbox/dead-events and /v1/retry/dead-events
-- endpoints. Uses hash-based change detection for efficient upserts.

-- =====================================================
-- 1. Table
-- =====================================================

CREATE TABLE lancer.discovered_dead_events (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    service_name            VARCHAR(255) NOT NULL,
    namespace               VARCHAR(255) NOT NULL,
    source                  VARCHAR(20)  NOT NULL,
    original_event_id       UUID         NOT NULL,
    event_type              VARCHAR(255),
    target_system           VARCHAR(255),
    attempts                INTEGER      NOT NULL DEFAULT 0,
    last_error_message      TEXT,
    last_error_stack_trace  TEXT,
    failed_at               TIMESTAMPTZ  NOT NULL,
    resolution              VARCHAR(20),
    resolved_at             TIMESTAMPTZ,
    content_hash            VARCHAR(64)  NOT NULL,
    last_seen_at            TIMESTAMPTZ  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT discovered_dead_events_pk PRIMARY KEY (id),
    CONSTRAINT uq_discovered_dead_events_svc_ns_src_eid
        UNIQUE (service_name, namespace, source, original_event_id)
);

-- =====================================================
-- 2. Indexes
-- =====================================================

CREATE INDEX idx_discovered_dead_events_status
    ON lancer.discovered_dead_events (status);

CREATE INDEX idx_discovered_dead_events_service_name
    ON lancer.discovered_dead_events (service_name);

CREATE INDEX idx_discovered_dead_events_namespace
    ON lancer.discovered_dead_events (namespace);

CREATE INDEX idx_discovered_dead_events_source
    ON lancer.discovered_dead_events (source);

CREATE INDEX idx_discovered_dead_events_content_hash
    ON lancer.discovered_dead_events (content_hash);

CREATE INDEX idx_discovered_dead_events_last_seen_at
    ON lancer.discovered_dead_events (last_seen_at);

CREATE INDEX idx_discovered_dead_events_failed_at
    ON lancer.discovered_dead_events (failed_at);

CREATE INDEX idx_discovered_dead_events_unresolved
    ON lancer.discovered_dead_events (status, failed_at)
    WHERE resolution IS NULL;
