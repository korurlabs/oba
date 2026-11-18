-- Village Database Schema - Iroh Sales Lead Discovery Runs
-- V1_2_47__Iroh_Sales_Lead_Discovery_Runs.sql
--
-- Creates iroh.sales_lead_discovery_runs, an immutable audit table that records
-- the outcome of each scheduled (or manually triggered) Claude lead-discovery
-- call against a single ICP.
--
-- Design decisions:
--   - No soft-delete — discovery runs are audit records and must never be removed.
--   - icp_id is SET NULL on ICP deletion so that the run history is preserved even
--     after an ICP is soft-deleted or hard-deleted.
--   - updated_at is nullable (NULL until first update) — matches BaseEntity pattern.
--   - duration_ms is nullable: a FAILED run may not have a meaningful duration.
--   - error_message is nullable: only populated when status = FAILED or PARTIAL.

-- =====================================================
-- 1. Create table
-- =====================================================

CREATE TABLE iroh.sales_lead_discovery_runs (
    id              UUID    NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID    NOT NULL,
    icp_id          UUID,
    run_date        DATE    NOT NULL,
    leads_found     INTEGER NOT NULL DEFAULT 0,
    leads_created   INTEGER NOT NULL DEFAULT 0,
    duration_ms     BIGINT,
    error_message   TEXT,
    status          iroh_discovery_run_status_enum NOT NULL DEFAULT 'COMPLETED',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT sales_lead_discovery_runs_pk
        PRIMARY KEY (id),
    CONSTRAINT sales_lead_discovery_runs_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT sales_lead_discovery_runs_icp_id_fk
        FOREIGN KEY (icp_id) REFERENCES iroh.sales_icps(id) ON DELETE SET NULL
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Primary query path: audit log filtered by org and date range.
CREATE INDEX sales_lead_discovery_runs_organization_id_run_date_idx
    ON iroh.sales_lead_discovery_runs(organization_id, run_date);

-- FK index required by convention.
CREATE INDEX sales_lead_discovery_runs_icp_id_idx
    ON iroh.sales_lead_discovery_runs(icp_id);
