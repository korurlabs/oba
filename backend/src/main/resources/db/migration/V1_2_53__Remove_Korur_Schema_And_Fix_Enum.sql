-- Remove Korur schema and clean approval enum
-- V1_2_53__Remove_Korur_Schema_And_Fix_Enum.sql
--
-- Korur has been moved to a separate project (projects/korur/kara) with its own database.
-- This migration:
--  1. Drops all 26 Korur tables from Village database
--  2. Drops all Korur ENUM types
--  3. Drops the korur schema
--  4. Removes Korur-related approvals and fixes shrine.approval_subject_type_enum (keep only PATCH_REQUEST)

-- ============================================================================
-- 1. Drop Korur tables (in reverse dependency order)
-- ============================================================================
-- Dependency graph (Parent -> Children):
--   suppliers -> subscriptions, subscription_seats, supplier_assessments, asset_assignments
--   subscriptions -> subscription_seats
--   assets -> asset_assignments
--   audits -> audit_findings -> corrective_actions
--   management_reviews -> management_review_actions
--   access_sources -> access_rights -> access_review_items
--   policies -> policy_versions, policy_control_mappings, access_reviews -> access_review_items
--   controls -> control_evidences, compliance_snapshots, policy_control_mappings, risks -> risk_treatments
--   risk_assessments -> risks
--   continuity_plans -> continuity_tests, recovery_objectives
-- ============================================================================

-- Leaf tables first (depend on others)
DROP TABLE IF EXISTS korur.access_review_items;      -- depends on access_reviews, access_rights
DROP TABLE IF EXISTS korur.management_review_actions; -- depends on management_reviews
DROP TABLE IF EXISTS korur.corrective_actions;        -- depends on audit_findings
DROP TABLE IF EXISTS korur.asset_assignments;         -- depends on assets, suppliers
DROP TABLE IF EXISTS korur.subscription_seats;        -- depends on subscriptions
DROP TABLE IF EXISTS korur.continuity_tests;          -- depends on continuity_plans
DROP TABLE IF EXISTS korur.recovery_objectives;       -- depends on continuity_plans
DROP TABLE IF EXISTS korur.risk_treatments;           -- depends on risks
DROP TABLE IF EXISTS korur.control_evidences;         -- depends on controls
DROP TABLE IF EXISTS korur.compliance_snapshots;      -- depends on controls
DROP TABLE IF EXISTS korur.policy_control_mappings;   -- depends on policies AND controls
DROP TABLE IF EXISTS korur.policy_versions;           -- depends on policies

-- Second level (depend only on leaf tables)
DROP TABLE IF EXISTS korur.access_reviews;           -- accessed by access_review_items (dropped)
DROP TABLE IF EXISTS korur.audit_findings;           -- accessed by corrective_actions (dropped)
DROP TABLE IF EXISTS korur.subscriptions;            -- accessed by subscription_seats (dropped)
DROP TABLE IF EXISTS korur.supplier_assessments;     -- no FK dependencies, safe to drop now
DROP TABLE IF EXISTS korur.risks;                    -- accessed by risk_treatments (dropped)

-- Parent tables (everything that depended on them is now gone)
DROP TABLE IF EXISTS korur.access_rights;            -- accessed by access_reviews (dropped)
DROP TABLE IF EXISTS korur.access_sources;           -- accessed by access_rights
DROP TABLE IF EXISTS korur.management_reviews;       -- accessed by management_review_actions (dropped)
DROP TABLE IF EXISTS korur.audits;                   -- accessed by audit_findings (dropped)
DROP TABLE IF EXISTS korur.assets;                   -- accessed by asset_assignments (dropped)
DROP TABLE IF EXISTS korur.suppliers;                -- accessed by subscriptions, asset_assignments (all dropped)
DROP TABLE IF EXISTS korur.continuity_plans;         -- accessed by continuity_tests, recovery_objectives (dropped)
DROP TABLE IF EXISTS korur.risk_assessments;         -- accessed by risks (dropped)
DROP TABLE IF EXISTS korur.controls;                 -- accessed by control_evidences, compliance_snapshots, policy_control_mappings, risks (all dropped)
DROP TABLE IF EXISTS korur.policies;                 -- accessed by policy_versions, policy_control_mappings, access_reviews (all dropped)
DROP TABLE IF EXISTS korur.security_objectives;      -- independent parent table
DROP TABLE IF EXISTS korur.isms_scopes;              -- independent parent table
DROP TABLE IF EXISTS korur.control_templates;        -- independent parent table
DROP TABLE IF EXISTS korur.improvements;             -- independent parent table
DROP TABLE IF EXISTS korur.backupables;              -- independent parent table

-- ============================================================================
-- 2. Drop all ENUM types in the korur schema
-- ============================================================================

DROP TYPE IF EXISTS korur.control_control_category_enum;
DROP TYPE IF EXISTS korur.control_control_type_enum;
DROP TYPE IF EXISTS korur.control_implementation_status_enum;
DROP TYPE IF EXISTS korur.control_evidence_type_enum;
DROP TYPE IF EXISTS korur.control_objective_status_enum;
DROP TYPE IF EXISTS korur.control_snapshot_trigger_enum;
DROP TYPE IF EXISTS korur.data_classification_enum;
DROP TYPE IF EXISTS korur.risk_risk_category_enum;
DROP TYPE IF EXISTS korur.risk_risk_level_enum;
DROP TYPE IF EXISTS korur.risk_risk_status_enum;
DROP TYPE IF EXISTS korur.risk_assessment_status_enum;
DROP TYPE IF EXISTS korur.risk_treatment_strategy_enum;
DROP TYPE IF EXISTS korur.risk_treatment_status_enum;
DROP TYPE IF EXISTS korur.policy_policy_category_enum;
DROP TYPE IF EXISTS korur.policy_policy_status_enum;
DROP TYPE IF EXISTS korur.audit_audit_type_enum;
DROP TYPE IF EXISTS korur.audit_audit_status_enum;
DROP TYPE IF EXISTS korur.audit_finding_severity_enum;
DROP TYPE IF EXISTS korur.audit_finding_status_enum;
DROP TYPE IF EXISTS korur.audit_action_status_enum;
DROP TYPE IF EXISTS korur.audit_improvement_source_enum;
DROP TYPE IF EXISTS korur.audit_improvement_status_enum;
DROP TYPE IF EXISTS korur.supplier_supplier_type_enum;
DROP TYPE IF EXISTS korur.supplier_supplier_criticality_enum;
DROP TYPE IF EXISTS korur.supplier_supplier_status_enum;
DROP TYPE IF EXISTS korur.supplier_data_classification_enum;
DROP TYPE IF EXISTS korur.rbac_source_type_enum;
DROP TYPE IF EXISTS korur.rbac_sync_status_enum;
DROP TYPE IF EXISTS korur.rbac_identity_type_enum;
DROP TYPE IF EXISTS korur.rbac_resource_type_enum;
DROP TYPE IF EXISTS korur.rbac_access_level_enum;
DROP TYPE IF EXISTS korur.rbac_review_decision_enum;
DROP TYPE IF EXISTS korur.rbac_review_type_enum;
DROP TYPE IF EXISTS korur.rbac_review_status_enum;
DROP TYPE IF EXISTS korur.bcp_plan_type_enum;
DROP TYPE IF EXISTS korur.bcp_plan_status_enum;
DROP TYPE IF EXISTS korur.bcp_test_type_enum;
DROP TYPE IF EXISTS korur.bcp_test_result_enum;
DROP TYPE IF EXISTS korur.subscription_subscription_type_enum;
DROP TYPE IF EXISTS korur.subscription_subscription_status_enum;
DROP TYPE IF EXISTS korur.subscription_billing_cycle_enum;
DROP TYPE IF EXISTS korur.asset_asset_type_enum;
DROP TYPE IF EXISTS korur.asset_asset_status_enum;
DROP TYPE IF EXISTS korur.asset_assignment_status_enum;

-- ============================================================================
-- 3. Drop the korur schema
-- ============================================================================

DROP SCHEMA IF EXISTS korur;

-- ============================================================================
-- 4. Remove Korur-related values from shrine.approval_subject_type_enum
-- ============================================================================
-- The enum currently contains: POLICY, BCP_PLAN, ISMS_SCOPE, IMPROVEMENT, PATCH_REQUEST
-- We need to keep only: PATCH_REQUEST
-- Two tables use this enum: shrine.approvals and shrine.acknowledgements
-- Delete any existing records with Korur-related subjects

DELETE FROM shrine.approvals
WHERE subject_type IN ('POLICY', 'BCP_PLAN', 'ISMS_SCOPE', 'IMPROVEMENT');

DELETE FROM shrine.acknowledgements
WHERE subject_type IN ('POLICY', 'BCP_PLAN', 'ISMS_SCOPE', 'IMPROVEMENT');

-- Create new enum with only valid value (PATCH_REQUEST)
CREATE TYPE shrine.approval_subject_type_enum_new AS ENUM ('PATCH_REQUEST');

-- Migrate shrine.approvals to new enum type (only PATCH_REQUEST records remain)
ALTER TABLE shrine.approvals
    ALTER COLUMN subject_type TYPE shrine.approval_subject_type_enum_new USING subject_type::text::shrine.approval_subject_type_enum_new;

-- Migrate shrine.acknowledgements to new enum type (only PATCH_REQUEST records remain)
ALTER TABLE shrine.acknowledgements
    ALTER COLUMN subject_type TYPE shrine.approval_subject_type_enum_new USING subject_type::text::shrine.approval_subject_type_enum_new;

-- Drop old enum and rename new one
DROP TYPE shrine.approval_subject_type_enum;
ALTER TYPE shrine.approval_subject_type_enum_new RENAME TO approval_subject_type_enum;
