-- Oba Database Schema - Dojo Create Api Tokens
-- V1_2_128__Dojo_Create_Api_Tokens.sql
--
-- Creates dojo.api_tokens, the one table of opaque API tokens that a machine
-- or an agent presents as a bearer credential, and copies every row of
-- kaya.machine_tokens into it so that issued kyt_ tokens keep working.
--
-- Only the SHA-256 hash of a token is stored. subject_id names the thing the
-- token acts for (a Kaya machine id now, an agent user id later) and has no
-- foreign key, because its target table depends on the kind. user_id is the
-- owner and always a person or agent row of shrine.users.
--
-- Delete rules: deleting a user or an organization deletes its tokens
-- (ON DELETE CASCADE), as kaya.machine_tokens did. Deleting a Kaya machine no
-- longer cascades to its token here, because subject_id has no foreign key;
-- the application revokes the token when it retires the machine.
--
-- Indexes:
--   api_tokens_token_hash_uk       lookup of a presented token by its hash.
--   api_tokens_kind_subject_id_uk  at most one unrevoked token per kind and
--                                  subject (partial), as the Kaya table had
--                                  per machine; also serves the live-token
--                                  lookup by kind and subject.
--   api_tokens_user_id_idx         foreign key, revoke of all tokens of a user.
--   api_tokens_organization_id_idx foreign key, revoke of all tokens of a member.
--
-- The copy keeps id, hash, prefix and every date. kaya.machine_tokens is not
-- changed or dropped here; a later migration drops it one release later.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE apitoken_api_token_kind_enum AS ENUM ('KAYA_MACHINE', 'AGENT');

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE dojo.api_tokens (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    subject_id UUID NOT NULL,
    token_prefix VARCHAR(12) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    kind apitoken_api_token_kind_enum NOT NULL,
    user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT api_tokens_pk PRIMARY KEY (id),
    CONSTRAINT api_tokens_token_hash_uk UNIQUE (token_hash),
    CONSTRAINT api_tokens_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id) ON DELETE CASCADE,
    CONSTRAINT api_tokens_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE UNIQUE INDEX api_tokens_kind_subject_id_uk ON dojo.api_tokens USING btree (kind, subject_id) WHERE (revoked_at IS NULL);
CREATE INDEX api_tokens_user_id_idx ON dojo.api_tokens USING btree (user_id);
CREATE INDEX api_tokens_organization_id_idx ON dojo.api_tokens USING btree (organization_id);

-- =====================================================
-- 4. Copy the Kaya machine tokens
-- =====================================================

INSERT INTO dojo.api_tokens (
    id, subject_id, token_prefix, token_hash, last_used_at, expires_at, revoked_at,
    kind, user_id, organization_id, created_at, updated_at
)
SELECT
    t.id, t.machine_id, t.token_prefix, t.token_hash, t.last_used_at, t.expires_at, t.revoked_at,
    'KAYA_MACHINE'::apitoken_api_token_kind_enum, t.user_id, m.organization_id, t.created_at, t.updated_at
FROM kaya.machine_tokens t
JOIN kaya.machines m ON m.id = t.machine_id;
