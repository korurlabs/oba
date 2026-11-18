-- Village Database Schema - Iroh Seed Sales Board Columns
-- V1_2_20__Iroh_Seed_Sales_Board_Columns.sql
--
-- Seeds the five default board columns for every existing organisation.
-- One row per (organization_id, linked_status) pair is inserted;
-- the unique constraint on sales_board_columns ensures idempotency if
-- this migration is ever re-applied against a partially-seeded DB.
--
-- Column order reflects the canonical sales funnel:
--   0 – FIRST_CONTACT  (entry point)
--   1 – NEGOTIATION    (active deal)
--   2 – CONVERTED      (won)
--   3 – LOST           (lost deal — non-funnel outcome)
--   4 – INACTIVE       (dormant — non-funnel outcome)
--
-- New organisations created after this migration receive their board columns
-- via the BoardColumnSeederService on first access (application-level seeding).

INSERT INTO iroh.sales_board_columns (id, name, linked_status, column_order, organization_id, created_at, updated_at, deleted)
SELECT
    gen_random_uuid(),
    'First Contact',
    'FIRST_CONTACT',
    0,
    o.id,
    NOW(),
    NOW(),
    FALSE
FROM shrine.organizations o
ON CONFLICT (organization_id, linked_status) DO NOTHING;

INSERT INTO iroh.sales_board_columns (id, name, linked_status, column_order, organization_id, created_at, updated_at, deleted)
SELECT
    gen_random_uuid(),
    'Negotiation',
    'NEGOTIATION',
    1,
    o.id,
    NOW(),
    NOW(),
    FALSE
FROM shrine.organizations o
ON CONFLICT (organization_id, linked_status) DO NOTHING;

INSERT INTO iroh.sales_board_columns (id, name, linked_status, column_order, organization_id, created_at, updated_at, deleted)
SELECT
    gen_random_uuid(),
    'Converted',
    'CONVERTED',
    2,
    o.id,
    NOW(),
    NOW(),
    FALSE
FROM shrine.organizations o
ON CONFLICT (organization_id, linked_status) DO NOTHING;

INSERT INTO iroh.sales_board_columns (id, name, linked_status, column_order, organization_id, created_at, updated_at, deleted)
SELECT
    gen_random_uuid(),
    'Lost',
    'LOST',
    3,
    o.id,
    NOW(),
    NOW(),
    FALSE
FROM shrine.organizations o
ON CONFLICT (organization_id, linked_status) DO NOTHING;

INSERT INTO iroh.sales_board_columns (id, name, linked_status, column_order, organization_id, created_at, updated_at, deleted)
SELECT
    gen_random_uuid(),
    'Inactive',
    'INACTIVE',
    4,
    o.id,
    NOW(),
    NOW(),
    FALSE
FROM shrine.organizations o
ON CONFLICT (organization_id, linked_status) DO NOTHING;
