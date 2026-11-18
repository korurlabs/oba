-- Database Schema - Kaizen Add Alert Routes
-- V1_2_70__Kaizen_Add_Alert_Routes.sql
--
-- An Alertmanager alert names the system it comes from in its system label.
-- This table maps that system to the board that receives its incidents, so the
-- map is managed in the application instead of in an environment key. One row
-- may be the default, which receives the alerts of systems without a row.
-- No rows are seeded: board ids differ per environment, so an organization
-- admin enters the mappings after the deploy.

-- =====================================================
-- 1. Create tables
-- =====================================================

-- One row per system, enforced by the unique system_key constraint. Several
-- systems may point to the same board. ON DELETE CASCADE on board_id: a board
-- that is purged takes its mappings with it, so no mapping points to a board
-- that is gone. A soft-deleted board keeps its mappings, and the webhook then
-- refuses alerts routed to it.
CREATE TABLE kaizen.alert_routes (
    id UUID NOT NULL,
    system_key VARCHAR(50) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    board_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alert_routes_pk PRIMARY KEY (id),
    CONSTRAINT alert_routes_system_key_uk UNIQUE (system_key),
    CONSTRAINT alert_routes_board_id_fk FOREIGN KEY (board_id) REFERENCES kaizen.boards (id) ON DELETE CASCADE,
    CONSTRAINT alert_routes_system_key_ck CHECK (system_key ~ '^[a-z0-9][a-z0-9-]{0,49}$')
);

-- =====================================================
-- 2. Create indexes
-- =====================================================

-- Foreign key lookup, and the cascade when a board is purged.
CREATE INDEX alert_routes_board_id_idx ON kaizen.alert_routes (board_id);

-- At most one default row: the index holds only rows with is_default true and
-- is unique on that constant, so a second default is refused.
CREATE UNIQUE INDEX alert_routes_is_default_uk ON kaizen.alert_routes (is_default) WHERE is_default;
