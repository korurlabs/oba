-- Kaya production data import into Oba.
--
-- Copies the data of the retired Kaya database into schema kaya of the Oba
-- database, after the backend has applied the Kaya migrations (V1_2_85 and
-- V1_2_86). It writes no Flyway history.
--
-- It runs only on a database at V1_2_92 or lower: it writes the agent-named
-- tables and columns (kaya.agents, kaya.agent_tokens,
-- kaya.agent_usage_snapshots, agent_id) and relies on the agent-named enum
-- types and audit actions that V1_2_93 renames. V1_2_93 renames the imported
-- rows afterwards, together with all other Kaya data.
--
-- The source is a restore of the Kaya schemas into the Oba database under their
-- old names (account, agent, identity, session, ...), made as described in
-- README.md. Kaya's own users, organizations and memberships are not copied:
-- they are mapped onto shrine.users, shrine.organizations and
-- shrine.organization_members, and every user and organization reference of the
-- copied rows is rewritten to the platform id.
--
-- Usage (run with psql against the Oba database):
--   dry run, rolls everything back:  psql -d oba -f import.sql
--                                    (also with -v commit=0 or any value but 1)
--   real run, commits:               psql -d oba -v commit=1 -f import.sql
--
-- Fail-safe: one transaction, stops at the first error (nothing is written),
-- refuses to run when any kaya table already holds rows, so a second real run
-- changes nothing. The dry run leaves the restored source schemas in place; the
-- real run drops them after the row counts match.
--
-- Mapping:
--   Users: lower(email) equal to a non-deleted shrine.users row, the oldest one
--   when several match; otherwise a new shrine.users row with keycloak_id NULL
--   and the lower-cased Kaya email, linked to the Keycloak account on first
--   sign-in. Several Kaya users with the same email share one platform user.
--   Organizations: kaya_import.organization_map, which may be filled before the
--   run; each Kaya organization without a row is matched by lower(name) to a
--   non-deleted shrine.organizations row, else a new ACTIVE organization is
--   created. Retention goes to kaya.organization_settings.
--   Memberships: copied to shrine.organization_members with the same role and
--   status; an existing membership keeps the higher role.
--   Email domains and organization audit entries are not copied.

\set ON_ERROR_STOP on
\set QUIET on

BEGIN;

-- Guards -------------------------------------------------------------------

DO $guard$
DECLARE
    required TEXT[] := ARRAY[
        'identity.users',
        'identity.organizations',
        'identity.organization_members',
        'account.accounts',
        'blob.blobs',
        'pack.packs',
        'repository.repositories',
        'seed.seed_runs',
        'identity.user_audit_entries',
        'identity.user_git_emails',
        'account.account_users',
        'agent.agents',
        'lesson.lessons',
        'release.repository_packs',
        'version.versions',
        'agent.agent_tokens',
        'agent.agent_usage_snapshots',
        'agentsfile.agents_files',
        'convention.conventions',
        'guardrail.guardrails',
        'hook.hooks',
        'version.item_overrides',
        'reference.reference_files',
        'release.releases',
        'review.reviews',
        'role.roles',
        'rule.rules',
        'script.scripts',
        'settings.settings',
        'skill.skills',
        'subagent.subagents',
        'template.templates',
        'toollink.tool_links',
        'toolparameter.tool_parameters',
        'workflow.workflows',
        'rule.rule_conventions',
        'session.sessions',
        'skill.skill_conventions',
        'skill.skill_references',
        'skill.skill_scripts',
        'skill.skill_tool_parameters',
        'skill.skill_workflows',
        'plan.plans',
        'session.session_events',
        'session.session_handoff_snapshots',
        'session.session_links',
        'session.session_skill_uses',
        'session.session_subagents',
        'plan.plan_versions',
        'plan.plan_feedback'
    ];
    name TEXT;
    tbl RECORD;
    n BIGINT;
BEGIN
    FOREACH name IN ARRAY required LOOP
        IF to_regclass(name) IS NULL THEN
            RAISE EXCEPTION 'Source table % not found. Restore the Kaya schemas first (README.md), or the import already ran.', name;
        END IF;
    END LOOP;

    IF to_regclass('kaya.organization_settings') IS NULL THEN
        RAISE EXCEPTION 'Schema kaya is missing. Start the backend so Flyway applies V1_2_85 and V1_2_86.';
    END IF;

    FOR tbl IN SELECT table_name FROM information_schema.tables
               WHERE table_schema = 'kaya' AND table_type = 'BASE TABLE' LOOP
        EXECUTE format('SELECT count(*) FROM kaya.%I', tbl.table_name) INTO n;
        IF n > 0 THEN
            RAISE EXCEPTION 'kaya.% already holds % rows. The import runs once, into empty tables.', tbl.table_name, n;
        END IF;
    END LOOP;
END
$guard$;

-- Identity maps ------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS kaya_import;

CREATE TABLE IF NOT EXISTS kaya_import.organization_map (
    kaya_organization_id UUID NOT NULL PRIMARY KEY,
    platform_organization_id UUID NOT NULL REFERENCES shrine.organizations (id)
);

CREATE TABLE IF NOT EXISTS kaya_import.user_map (
    kaya_user_id UUID NOT NULL PRIMARY KEY,
    platform_user_id UUID NOT NULL REFERENCES shrine.users (id),
    created BOOLEAN NOT NULL
);

TRUNCATE kaya_import.user_map;

-- Organizations without a row in the map: match by name, else create.
WITH unmapped AS MATERIALIZED (
    SELECT o.id AS kaya_id,
           o.name,
           o.status::TEXT AS status,
           gen_random_uuid() AS new_id,
           (SELECT p.id FROM shrine.organizations p
            WHERE NOT p.deleted AND lower(p.name) = lower(o.name)
            ORDER BY p.created_at, p.id LIMIT 1) AS platform_id
    FROM identity.organizations o
    WHERE NOT EXISTS (SELECT 1 FROM kaya_import.organization_map m WHERE m.kaya_organization_id = o.id)
), created AS (
    INSERT INTO shrine.organizations (id, name, status)
    SELECT new_id, name, (CASE status WHEN 'ACTIVE' THEN 'ACTIVE' ELSE 'DISABLED' END)::shrine_organization_status_enum
    FROM unmapped WHERE platform_id IS NULL
    RETURNING id
)
INSERT INTO kaya_import.organization_map (kaya_organization_id, platform_organization_id)
SELECT kaya_id, COALESCE(platform_id, new_id) FROM unmapped;

DO $check$
BEGIN
    IF EXISTS (SELECT 1 FROM kaya_import.organization_map m
               WHERE NOT EXISTS (SELECT 1 FROM identity.organizations o WHERE o.id = m.kaya_organization_id)) THEN
        RAISE EXCEPTION 'kaya_import.organization_map holds a Kaya organization id that is not in the source.';
    END IF;
END
$check$;

-- Users that match a platform user by email.
INSERT INTO kaya_import.user_map (kaya_user_id, platform_user_id, created)
SELECT DISTINCT ON (u.id) u.id, p.id, FALSE
FROM identity.users u
JOIN shrine.users p ON NOT p.deleted AND lower(p.email) = lower(u.email)
ORDER BY u.id, p.created_at, p.id;

-- Users without a match: one new platform user per distinct email.
WITH unmatched AS MATERIALIZED (
    SELECT u.* FROM identity.users u
    WHERE NOT EXISTS (SELECT 1 FROM kaya_import.user_map m WHERE m.kaya_user_id = u.id)
), emails AS MATERIALIZED (
    SELECT DISTINCT ON (lower(email))
           lower(email) AS email,
           display_name,
           status::TEXT AS status,
           created_at,
           gen_random_uuid() AS new_id
    FROM unmatched
    ORDER BY lower(email), created_at, id
), created AS (
    INSERT INTO shrine.users (id, email, first_name, last_name, status, created_at, updated_at)
    SELECT new_id,
           email,
           NULLIF(split_part(btrim(display_name), ' ', 1), ''),
           NULLIF(btrim(substr(btrim(display_name), length(split_part(btrim(display_name), ' ', 1)) + 1)), ''),
           (CASE status WHEN 'ACTIVE' THEN 'ACTIVE' ELSE 'DISABLED' END)::shrine_identity_user_status_enum,
           created_at,
           CURRENT_TIMESTAMP
    FROM emails
    RETURNING id
)
INSERT INTO kaya_import.user_map (kaya_user_id, platform_user_id, created)
SELECT un.id, e.new_id, TRUE
FROM unmatched un
JOIN emails e ON e.email = lower(un.email);

-- Memberships --------------------------------------------------------------

CREATE TEMPORARY TABLE kaya_import_members ON COMMIT DROP AS
SELECT om.platform_organization_id AS organization_id,
       um.platform_user_id AS user_id,
       (CASE max(CASE m.role::TEXT WHEN 'OWNER' THEN 3 WHEN 'ADMIN' THEN 2 ELSE 1 END)
            WHEN 3 THEN 'OWNER' WHEN 2 THEN 'ADMIN' ELSE 'MEMBER' END) AS role,
       (CASE WHEN bool_or(m.status::TEXT = 'ACTIVE') THEN 'ACTIVE' ELSE 'DISABLED' END) AS status,
       min(m.created_at) AS created_at
FROM identity.organization_members m
JOIN kaya_import.organization_map om ON om.kaya_organization_id = m.organization_id
JOIN kaya_import.user_map um ON um.kaya_user_id = m.user_id
GROUP BY om.platform_organization_id, um.platform_user_id;

-- An existing membership keeps the higher role; its status is left as it is.
UPDATE shrine.organization_members e
SET role = k.role::shrine_organization_role_enum,
    updated_at = CURRENT_TIMESTAMP
FROM kaya_import_members k
WHERE e.organization_id = k.organization_id
  AND e.user_id = k.user_id
  AND NOT e.deleted
  AND CASE k.role WHEN 'OWNER' THEN 3 WHEN 'ADMIN' THEN 2 ELSE 1 END
    > CASE e.role::TEXT WHEN 'OWNER' THEN 3 WHEN 'ADMIN' THEN 2 WHEN 'MEMBER' THEN 1 ELSE 0 END;

INSERT INTO shrine.organization_members (id, organization_id, user_id, role, status, created_at, updated_at)
SELECT gen_random_uuid(), k.organization_id, k.user_id,
       k.role::shrine_organization_role_enum, k.status::shrine_organization_member_status_enum,
       k.created_at, CURRENT_TIMESTAMP
FROM kaya_import_members k
WHERE NOT EXISTS (
    SELECT 1 FROM shrine.organization_members e
    WHERE e.organization_id = k.organization_id AND e.user_id = k.user_id AND NOT e.deleted);

-- Kaya data, parents before children -----------------------------------------

INSERT INTO kaya.accounts (id, organization_id, provider, account_email, account_organization_uuid, account_organization_name, billing_type, organization_type, seat_tier, last_seen_at, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.provider,
       s.account_email,
       s.account_organization_uuid,
       s.account_organization_name,
       s.billing_type,
       s.organization_type,
       s.seat_tier,
       s.last_seen_at,
       s.created_at,
       s.updated_at
FROM account.accounts s;

INSERT INTO kaya.blobs (id, organization_id, sha256, content, size, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.sha256,
       s.content,
       s.size,
       s.created_at,
       s.updated_at
FROM blob.blobs s;

-- organization_settings: Kaya kept retention on its organizations table.
INSERT INTO kaya.organization_settings (organization_id, retention_days, created_at, updated_at)
SELECT m.platform_organization_id, o.retention_days, o.created_at, o.updated_at
FROM identity.organizations o
JOIN kaya_import.organization_map m ON m.kaya_organization_id = o.id;

INSERT INTO kaya.packs (id, organization_id, name, current_version_id, created_at, updated_at, required, parent_pack_id, description)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.name,
       NULL,
       s.created_at,
       s.updated_at,
       s.required,
       s.parent_pack_id,
       s.description
FROM pack.packs s;

INSERT INTO kaya.repositories (id, organization_id, name, remote_url, ticket_url_template, status, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.name,
       s.remote_url,
       s.ticket_url_template,
       s.status,
       s.created_at,
       s.updated_at
FROM repository.repositories s;

INSERT INTO kaya.seed_runs (id, organization_id, user_id, source_ref, status, report, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id),
       s.source_ref,
       s.status,
       s.report,
       s.created_at,
       s.updated_at
FROM seed.seed_runs s;

INSERT INTO kaya.user_audit_entries (id, organization_id, user_id, actor_kind, actor_id, action, reason, details, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id),
       s.actor_kind,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.actor_id),
       s.action,
       s.reason,
       s.details,
       s.created_at,
       s.updated_at
FROM identity.user_audit_entries s;

INSERT INTO kaya.user_git_emails (id, organization_id, user_id, email, source, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id),
       s.email,
       s.source,
       s.created_at,
       s.updated_at
FROM identity.user_git_emails s;

INSERT INTO kaya.account_users (id, organization_id, account_id, user_id, source, last_seen_at, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.account_id,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id),
       s.source,
       s.last_seen_at,
       s.created_at,
       s.updated_at
FROM account.account_users s;

INSERT INTO kaya.agents (id, organization_id, machine_name, claude_code_version, account_id, last_seen_at, status, created_at, updated_at, user_id)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.machine_name,
       s.claude_code_version,
       s.account_id,
       s.last_seen_at,
       s.status,
       s.created_at,
       s.updated_at,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id)
FROM agent.agents s;

INSERT INTO kaya.lessons (id, title, body, claude_session_id, organization_id, repository_id, user_id, created_at, updated_at)
SELECT s.id,
       s.title,
       s.body,
       s.claude_session_id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.repository_id,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id),
       s.created_at,
       s.updated_at
FROM lesson.lessons s;

INSERT INTO kaya.repository_packs (id, repository_id, pack_id, position, created_at, updated_at, root_path)
SELECT s.id,
       s.repository_id,
       s.pack_id,
       s.position,
       s.created_at,
       s.updated_at,
       s.root_path
FROM release.repository_packs s;

INSERT INTO kaya.versions (id, organization_id, pack_id, number, status, base_version_id, proposed_by, source, reason, executable_change, manifest_sha256, submitted_at, decided_at, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.pack_id,
       s.number,
       s.status,
       s.base_version_id,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.proposed_by),
       s.source,
       s.reason,
       s.executable_change,
       s.manifest_sha256,
       s.submitted_at,
       s.decided_at,
       s.created_at,
       s.updated_at
FROM version.versions s;

-- packs and versions reference each other: current_version_id is set once both exist.
UPDATE kaya.packs p
SET current_version_id = s.current_version_id
FROM pack.packs s
WHERE s.id = p.id AND s.current_version_id IS NOT NULL;

INSERT INTO kaya.agent_tokens (id, agent_id, token_prefix, token_hash, last_used_at, expires_at, revoked_at, created_at, updated_at, user_id)
SELECT s.id,
       s.agent_id,
       s.token_prefix,
       s.token_hash,
       s.last_used_at,
       s.expires_at,
       s.revoked_at,
       s.created_at,
       s.updated_at,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id)
FROM agent.agent_tokens s;

INSERT INTO kaya.agent_usage_snapshots (id, organization_id, agent_id, account_id, session_id, five_hour_used_percentage, five_hour_resets_at, seven_day_used_percentage, seven_day_resets_at, captured_at, received_at, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.agent_id,
       s.account_id,
       s.session_id,
       s.five_hour_used_percentage,
       s.five_hour_resets_at,
       s.seven_day_used_percentage,
       s.seven_day_resets_at,
       s.captured_at,
       s.received_at,
       s.created_at,
       s.updated_at
FROM agent.agent_usage_snapshots s;

INSERT INTO kaya.agents_files (id, placement, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.placement,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM agentsfile.agents_files s;

INSERT INTO kaya.conventions (id, relative_path, title, overrides_path, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.relative_path,
       s.title,
       s.overrides_path,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM convention.conventions s;

INSERT INTO kaya.guardrails (id, name, category, description, deny, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.name,
       s.category,
       s.description,
       s.deny,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM guardrail.guardrails s;

INSERT INTO kaya.hooks (id, relative_path, executable, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.relative_path,
       s.executable,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM hook.hooks s;

INSERT INTO kaya.item_overrides (id, organization_id, version_id, path, base_pack_id, base_version_id, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.path,
       s.base_pack_id,
       s.base_version_id,
       s.created_at,
       s.updated_at
FROM version.item_overrides s;

INSERT INTO kaya.reference_files (id, name, media_type, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.name,
       s.media_type,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM reference.reference_files s;

INSERT INTO kaya.releases (id, repository_id, number, cause_version_id, manifest, manifest_sha256, signature, key_id, created_at, updated_at, pack_versions)
SELECT s.id,
       s.repository_id,
       s.number,
       s.cause_version_id,
       s.manifest,
       s.manifest_sha256,
       s.signature,
       s.key_id,
       s.created_at,
       s.updated_at,
       s.pack_versions
FROM release.releases s;

INSERT INTO kaya.reviews (id, version_id, reviewer_id, decision, comment, created_at, updated_at)
SELECT s.id,
       s.version_id,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.reviewer_id),
       s.decision,
       s.comment,
       s.created_at,
       s.updated_at
FROM review.reviews s;

INSERT INTO kaya.roles (id, relative_path, title, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.relative_path,
       s.title,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM role.roles s;

INSERT INTO kaya.rules (id, relative_path, description, paths, always_on, overrides_path, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.relative_path,
       s.description,
       s.paths,
       s.always_on,
       s.overrides_path,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM rule.rules s;

INSERT INTO kaya.scripts (id, name, executable, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.name,
       s.executable,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM script.scripts s;

INSERT INTO kaya.settings (id, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM settings.settings s;

INSERT INTO kaya.skills (id, name, description, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.name,
       s.description,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM skill.skills s;

INSERT INTO kaya.subagents (id, relative_path, name, description, model, max_turns, tools, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.relative_path,
       s.name,
       s.description,
       s.model,
       s.max_turns,
       s.tools,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM subagent.subagents s;

INSERT INTO kaya.templates (id, relative_path, title, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.relative_path,
       s.title,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM template.templates s;

INSERT INTO kaya.tool_links (id, organization_id, version_id, name, created_at, updated_at)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.name,
       s.created_at,
       s.updated_at
FROM toollink.tool_links s;

INSERT INTO kaya.tool_parameters (id, name, format, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.name,
       s.format,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM toolparameter.tool_parameters s;

INSERT INTO kaya.workflows (id, relative_path, title, organization_id, version_id, content_blob_id, created_at, updated_at)
SELECT s.id,
       s.relative_path,
       s.title,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.version_id,
       s.content_blob_id,
       s.created_at,
       s.updated_at
FROM workflow.workflows s;

INSERT INTO kaya.rule_conventions (id, rule_id, convention_path, created_at, updated_at)
SELECT s.id,
       s.rule_id,
       s.convention_path,
       s.created_at,
       s.updated_at
FROM rule.rule_conventions s;

INSERT INTO kaya.sessions (id, organization_id, repository_id, agent_id, session_id, title, session_title, first_prompt, branch, cwd, model, claude_code_version, account_email, account_organization_uuid, account_organization_name, billing_type, seat_tier, git_author_email, started_at, last_event_at, ended, event_count, features, created_at, updated_at, release_id, release_state, delivered_by, user_id, identity_conflict_user_id)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.repository_id,
       s.agent_id,
       s.session_id,
       s.title,
       s.session_title,
       s.first_prompt,
       s.branch,
       s.cwd,
       s.model,
       s.claude_code_version,
       s.account_email,
       s.account_organization_uuid,
       s.account_organization_name,
       s.billing_type,
       s.seat_tier,
       s.git_author_email,
       s.started_at,
       s.last_event_at,
       s.ended,
       s.event_count,
       s.features,
       s.created_at,
       s.updated_at,
       s.release_id,
       s.release_state,
       s.delivered_by,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id),
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.identity_conflict_user_id)
FROM session.sessions s;

INSERT INTO kaya.skill_conventions (id, skill_id, convention_id, path, created_at, updated_at)
SELECT s.id,
       s.skill_id,
       s.convention_id,
       s.path,
       s.created_at,
       s.updated_at
FROM skill.skill_conventions s;

INSERT INTO kaya.skill_references (id, skill_id, reference_id, path, created_at, updated_at)
SELECT s.id,
       s.skill_id,
       s.reference_id,
       s.path,
       s.created_at,
       s.updated_at
FROM skill.skill_references s;

INSERT INTO kaya.skill_scripts (id, skill_id, script_id, path, created_at, updated_at)
SELECT s.id,
       s.skill_id,
       s.script_id,
       s.path,
       s.created_at,
       s.updated_at
FROM skill.skill_scripts s;

INSERT INTO kaya.skill_tool_parameters (id, skill_id, tool_parameter_id, path, created_at, updated_at)
SELECT s.id,
       s.skill_id,
       s.tool_parameter_id,
       s.path,
       s.created_at,
       s.updated_at
FROM skill.skill_tool_parameters s;

INSERT INTO kaya.skill_workflows (id, skill_id, workflow_id, path, created_at, updated_at)
SELECT s.id,
       s.skill_id,
       s.workflow_id,
       s.path,
       s.created_at,
       s.updated_at
FROM skill.skill_workflows s;

INSERT INTO kaya.plans (id, organization_id, repository_id, kind, path, session_id, title, feature, review_state, latest_version_number, latest_session_id, open_feedback_count, last_changed_at, created_at, updated_at, user_id, pack_id)
SELECT s.id,
       (SELECT m.platform_organization_id FROM kaya_import.organization_map m WHERE m.kaya_organization_id = s.organization_id),
       s.repository_id,
       s.kind,
       s.path,
       s.session_id,
       s.title,
       s.feature,
       s.review_state,
       s.latest_version_number,
       s.latest_session_id,
       s.open_feedback_count,
       s.last_changed_at,
       s.created_at,
       s.updated_at,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.user_id),
       s.pack_id
FROM plan.plans s;

INSERT INTO kaya.session_events (id, session_id, seq, at, event, agent_id, payload, created_at, updated_at)
SELECT s.id,
       s.session_id,
       s.seq,
       s.at,
       s.event,
       s.agent_id,
       s.payload,
       s.created_at,
       s.updated_at
FROM session.session_events s;

INSERT INTO kaya.session_handoff_snapshots (id, session_id, path, content, captured_at, created_at, updated_at)
SELECT s.id,
       s.session_id,
       s.path,
       s.content,
       s.captured_at,
       s.created_at,
       s.updated_at
FROM session.session_handoff_snapshots s;

INSERT INTO kaya.session_links (id, session_id, kind, reference, url, source, created_by, created_at, updated_at)
SELECT s.id,
       s.session_id,
       s.kind,
       s.reference,
       s.url,
       s.source,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.created_by),
       s.created_at,
       s.updated_at
FROM session.session_links s;

INSERT INTO kaya.session_skill_uses (id, session_id, skill, source, use_count, first_used_at, created_at, updated_at)
SELECT s.id,
       s.session_id,
       s.skill,
       s.source,
       s.use_count,
       s.first_used_at,
       s.created_at,
       s.updated_at
FROM session.session_skill_uses s;

INSERT INTO kaya.session_subagents (id, session_id, agent_id, subagent_type, model, description, started_at, ended_at, created_at, updated_at)
SELECT s.id,
       s.session_id,
       s.agent_id,
       s.subagent_type,
       s.model,
       s.description,
       s.started_at,
       s.ended_at,
       s.created_at,
       s.updated_at
FROM session.session_subagents s;

INSERT INTO kaya.plan_versions (id, plan_id, number, source, session_id, content, content_sha256, line_count, captured_at, created_at, updated_at)
SELECT s.id,
       s.plan_id,
       s.number,
       s.source,
       s.session_id,
       s.content,
       s.content_sha256,
       s.line_count,
       s.captured_at,
       s.created_at,
       s.updated_at
FROM plan.plan_versions s;

INSERT INTO kaya.plan_feedback (id, plan_id, version_id, reviewer_id, decision, comment, line_start, line_end, quote, delivered_at, delivered_session_id, created_at, updated_at)
SELECT s.id,
       s.plan_id,
       s.version_id,
       (SELECT m.platform_user_id FROM kaya_import.user_map m WHERE m.kaya_user_id = s.reviewer_id),
       s.decision,
       s.comment,
       s.line_start,
       s.line_end,
       s.quote,
       s.delivered_at,
       s.delivered_session_id,
       s.created_at,
       s.updated_at
FROM plan.plan_feedback s;

-- Verification -------------------------------------------------------------

DO $verify$
DECLARE
    r RECORD;
    bad TEXT := '';
BEGIN
    FOR r IN SELECT * FROM (VALUES
    ('accounts', (SELECT count(*) FROM account.accounts), (SELECT count(*) FROM kaya.accounts)),
    ('blobs', (SELECT count(*) FROM blob.blobs), (SELECT count(*) FROM kaya.blobs)),
    ('organization_settings', (SELECT count(*) FROM identity.organizations), (SELECT count(*) FROM kaya.organization_settings)),
    ('packs', (SELECT count(*) FROM pack.packs), (SELECT count(*) FROM kaya.packs)),
    ('repositories', (SELECT count(*) FROM repository.repositories), (SELECT count(*) FROM kaya.repositories)),
    ('seed_runs', (SELECT count(*) FROM seed.seed_runs), (SELECT count(*) FROM kaya.seed_runs)),
    ('user_audit_entries', (SELECT count(*) FROM identity.user_audit_entries), (SELECT count(*) FROM kaya.user_audit_entries)),
    ('user_git_emails', (SELECT count(*) FROM identity.user_git_emails), (SELECT count(*) FROM kaya.user_git_emails)),
    ('account_users', (SELECT count(*) FROM account.account_users), (SELECT count(*) FROM kaya.account_users)),
    ('agents', (SELECT count(*) FROM agent.agents), (SELECT count(*) FROM kaya.agents)),
    ('lessons', (SELECT count(*) FROM lesson.lessons), (SELECT count(*) FROM kaya.lessons)),
    ('repository_packs', (SELECT count(*) FROM release.repository_packs), (SELECT count(*) FROM kaya.repository_packs)),
    ('versions', (SELECT count(*) FROM version.versions), (SELECT count(*) FROM kaya.versions)),
    ('agent_tokens', (SELECT count(*) FROM agent.agent_tokens), (SELECT count(*) FROM kaya.agent_tokens)),
    ('agent_usage_snapshots', (SELECT count(*) FROM agent.agent_usage_snapshots), (SELECT count(*) FROM kaya.agent_usage_snapshots)),
    ('agents_files', (SELECT count(*) FROM agentsfile.agents_files), (SELECT count(*) FROM kaya.agents_files)),
    ('conventions', (SELECT count(*) FROM convention.conventions), (SELECT count(*) FROM kaya.conventions)),
    ('guardrails', (SELECT count(*) FROM guardrail.guardrails), (SELECT count(*) FROM kaya.guardrails)),
    ('hooks', (SELECT count(*) FROM hook.hooks), (SELECT count(*) FROM kaya.hooks)),
    ('item_overrides', (SELECT count(*) FROM version.item_overrides), (SELECT count(*) FROM kaya.item_overrides)),
    ('reference_files', (SELECT count(*) FROM reference.reference_files), (SELECT count(*) FROM kaya.reference_files)),
    ('releases', (SELECT count(*) FROM release.releases), (SELECT count(*) FROM kaya.releases)),
    ('reviews', (SELECT count(*) FROM review.reviews), (SELECT count(*) FROM kaya.reviews)),
    ('roles', (SELECT count(*) FROM role.roles), (SELECT count(*) FROM kaya.roles)),
    ('rules', (SELECT count(*) FROM rule.rules), (SELECT count(*) FROM kaya.rules)),
    ('scripts', (SELECT count(*) FROM script.scripts), (SELECT count(*) FROM kaya.scripts)),
    ('settings', (SELECT count(*) FROM settings.settings), (SELECT count(*) FROM kaya.settings)),
    ('skills', (SELECT count(*) FROM skill.skills), (SELECT count(*) FROM kaya.skills)),
    ('subagents', (SELECT count(*) FROM subagent.subagents), (SELECT count(*) FROM kaya.subagents)),
    ('templates', (SELECT count(*) FROM template.templates), (SELECT count(*) FROM kaya.templates)),
    ('tool_links', (SELECT count(*) FROM toollink.tool_links), (SELECT count(*) FROM kaya.tool_links)),
    ('tool_parameters', (SELECT count(*) FROM toolparameter.tool_parameters), (SELECT count(*) FROM kaya.tool_parameters)),
    ('workflows', (SELECT count(*) FROM workflow.workflows), (SELECT count(*) FROM kaya.workflows)),
    ('rule_conventions', (SELECT count(*) FROM rule.rule_conventions), (SELECT count(*) FROM kaya.rule_conventions)),
    ('sessions', (SELECT count(*) FROM session.sessions), (SELECT count(*) FROM kaya.sessions)),
    ('skill_conventions', (SELECT count(*) FROM skill.skill_conventions), (SELECT count(*) FROM kaya.skill_conventions)),
    ('skill_references', (SELECT count(*) FROM skill.skill_references), (SELECT count(*) FROM kaya.skill_references)),
    ('skill_scripts', (SELECT count(*) FROM skill.skill_scripts), (SELECT count(*) FROM kaya.skill_scripts)),
    ('skill_tool_parameters', (SELECT count(*) FROM skill.skill_tool_parameters), (SELECT count(*) FROM kaya.skill_tool_parameters)),
    ('skill_workflows', (SELECT count(*) FROM skill.skill_workflows), (SELECT count(*) FROM kaya.skill_workflows)),
    ('plans', (SELECT count(*) FROM plan.plans), (SELECT count(*) FROM kaya.plans)),
    ('session_events', (SELECT count(*) FROM session.session_events), (SELECT count(*) FROM kaya.session_events)),
    ('session_handoff_snapshots', (SELECT count(*) FROM session.session_handoff_snapshots), (SELECT count(*) FROM kaya.session_handoff_snapshots)),
    ('session_links', (SELECT count(*) FROM session.session_links), (SELECT count(*) FROM kaya.session_links)),
    ('session_skill_uses', (SELECT count(*) FROM session.session_skill_uses), (SELECT count(*) FROM kaya.session_skill_uses)),
    ('session_subagents', (SELECT count(*) FROM session.session_subagents), (SELECT count(*) FROM kaya.session_subagents)),
    ('plan_versions', (SELECT count(*) FROM plan.plan_versions), (SELECT count(*) FROM kaya.plan_versions)),
    ('plan_feedback', (SELECT count(*) FROM plan.plan_feedback), (SELECT count(*) FROM kaya.plan_feedback))
    ) AS c (table_name, source_rows, copied_rows) LOOP
        RAISE NOTICE 'kaya.% source % copied %', r.table_name, r.source_rows, r.copied_rows;
        IF r.source_rows <> r.copied_rows THEN
            bad := bad || ' ' || r.table_name;
        END IF;
    END LOOP;
    IF bad <> '' THEN
        RAISE EXCEPTION 'Row count mismatch in:%', bad;
    END IF;
END
$verify$;

SELECT 'users: matched ' || count(*) FILTER (WHERE NOT created)
    || ', created ' || count(*) FILTER (WHERE created)
    || ', kaya users ' || count(*) AS user_map
FROM kaya_import.user_map;

SELECT 'organizations mapped: ' || count(*) AS organization_map FROM kaya_import.organization_map;

-- Commit only for -v commit=1: any other value, commit=0 included, is a dry run.
\if :{?commit}
    SELECT :'commit' = '1' AS import_commit \gset
\else
    \set import_commit false
\endif

\if :import_commit
    SET LOCAL client_min_messages = warning;
    DROP SCHEMA account, agent, agentsfile, blob, convention, guardrail, hook, identity, lesson, pack, plan, reference, release, repository, review, role, rule, script, seed, session, settings, skill, subagent, template, toollink, toolparameter, version, workflow CASCADE;
    DROP TYPE IF EXISTS public.identity_organization_audit_entry_action_enum,
                        public.identity_organization_member_status_enum,
                        public.identity_organization_role_enum,
                        public.identity_organization_status_enum,
                        public.identity_user_status_enum;
    COMMIT;
    \echo 'COMMITTED: Kaya data imported, restored source schemas dropped.'
\else
    ROLLBACK;
    \echo 'DRY RUN: rolled back, nothing changed. Run again with -v commit=1 to commit.'
\endif
