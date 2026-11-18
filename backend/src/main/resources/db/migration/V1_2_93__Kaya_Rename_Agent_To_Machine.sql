-- Oba Database Schema - Kaya Rename Agent To Machine
-- V1_2_93__Kaya_Rename_Agent_To_Machine.sql
--
-- Renames the Kaya recording machine (formerly "agent") to "machine": the
-- tables kaya.agents, kaya.agent_tokens and kaya.agent_usage_snapshots, the
-- agent_id column of the token, usage snapshot and session tables, the
-- status enum type, the two audit action enum values, every constraint and
-- index that embeds the old name, and the agentId key of the audit details.
--
-- Kept on purpose: kaya.session_events.agent_id and
-- kaya.session_subagents.agent_id (Claude Code subagent ids), kaya.agents_files
-- and its constraints and indexes (agent configuration files), and the Sage
-- agent tables of the ai schema.
--
-- ALTER TYPE ... RENAME VALUE is an in-place rename: stored rows follow the
-- new value and no table rewrite is needed.

-- =====================================================
-- 1. Rename tables
-- =====================================================

ALTER TABLE kaya.agents                  RENAME TO machines;
ALTER TABLE kaya.agent_tokens            RENAME TO machine_tokens;
ALTER TABLE kaya.agent_usage_snapshots   RENAME TO machine_usage_snapshots;

-- =====================================================
-- 2. Rename agent_id columns (not session_events or session_subagents)
-- =====================================================

ALTER TABLE kaya.machine_tokens             RENAME COLUMN agent_id TO machine_id;
ALTER TABLE kaya.machine_usage_snapshots    RENAME COLUMN agent_id TO machine_id;
ALTER TABLE kaya.sessions                   RENAME COLUMN agent_id TO machine_id;

-- =====================================================
-- 3. Rename enum type and audit action values
-- =====================================================

ALTER TYPE agent_agent_status_enum RENAME TO machine_machine_status_enum;

ALTER TYPE identity_user_audit_entry_action_enum RENAME VALUE 'AGENT_CREATED' TO 'MACHINE_CREATED';
ALTER TYPE identity_user_audit_entry_action_enum RENAME VALUE 'AGENT_RETIRED' TO 'MACHINE_RETIRED';

-- =====================================================
-- 4. Rename constraints
-- =====================================================

ALTER TABLE kaya.machines RENAME CONSTRAINT agents_pk TO machines_pk;
ALTER TABLE kaya.machines RENAME CONSTRAINT agents_organization_id_user_id_machine_name_uk TO machines_organization_id_user_id_machine_name_uk;
ALTER TABLE kaya.machines RENAME CONSTRAINT agents_account_id_fk TO machines_account_id_fk;
ALTER TABLE kaya.machines RENAME CONSTRAINT agents_organization_id_fk TO machines_organization_id_fk;
ALTER TABLE kaya.machines RENAME CONSTRAINT agents_user_id_fk TO machines_user_id_fk;
ALTER TABLE kaya.machine_tokens RENAME CONSTRAINT agent_tokens_pk TO machine_tokens_pk;
ALTER TABLE kaya.machine_tokens RENAME CONSTRAINT agent_tokens_token_hash_uk TO machine_tokens_token_hash_uk;
ALTER TABLE kaya.machine_tokens RENAME CONSTRAINT agent_tokens_agent_id_fk TO machine_tokens_machine_id_fk;
ALTER TABLE kaya.machine_tokens RENAME CONSTRAINT agent_tokens_user_id_fk TO machine_tokens_user_id_fk;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_pk TO machine_usage_snapshots_pk;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_agent_id_uk TO machine_usage_snapshots_machine_id_uk;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_five_hour_used_percentage_ck TO machine_usage_snapshots_five_hour_used_percentage_ck;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_five_hour_window_ck TO machine_usage_snapshots_five_hour_window_ck;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_seven_day_used_percentage_ck TO machine_usage_snapshots_seven_day_used_percentage_ck;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_seven_day_window_ck TO machine_usage_snapshots_seven_day_window_ck;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_account_id_fk TO machine_usage_snapshots_account_id_fk;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_agent_id_fk TO machine_usage_snapshots_machine_id_fk;
ALTER TABLE kaya.machine_usage_snapshots RENAME CONSTRAINT agent_usage_snapshots_organization_id_fk TO machine_usage_snapshots_organization_id_fk;
ALTER TABLE kaya.sessions RENAME CONSTRAINT sessions_agent_id_fk TO sessions_machine_id_fk;

-- =====================================================
-- 5. Rename indexes
-- =====================================================

ALTER INDEX kaya.agents_account_id_idx RENAME TO machines_account_id_idx;
ALTER INDEX kaya.agents_user_id_idx RENAME TO machines_user_id_idx;
ALTER INDEX kaya.agent_tokens_agent_id_active_uk RENAME TO machine_tokens_machine_id_active_uk;
ALTER INDEX kaya.agent_tokens_agent_id_idx RENAME TO machine_tokens_machine_id_idx;
ALTER INDEX kaya.agent_tokens_user_id_idx RENAME TO machine_tokens_user_id_idx;
ALTER INDEX kaya.agent_usage_snapshots_account_id_captured_at_idx RENAME TO machine_usage_snapshots_account_id_captured_at_idx;
ALTER INDEX kaya.agent_usage_snapshots_organization_id_idx RENAME TO machine_usage_snapshots_organization_id_idx;
ALTER INDEX kaya.sessions_agent_id_idx RENAME TO sessions_machine_id_idx;

-- =====================================================
-- 6. Move the audit detail key agentId to machineId
-- =====================================================

UPDATE kaya.user_audit_entries
SET details = (details - 'agentId') || jsonb_build_object('machineId', details -> 'agentId')
WHERE details ? 'agentId';
