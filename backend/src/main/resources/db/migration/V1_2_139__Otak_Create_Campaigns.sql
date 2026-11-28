-- Oba Database Schema - Otak: Create Campaigns
-- V1_2_139__Otak_Create_Campaigns.sql
--
-- Adds the campaign of an organization to Otak and its contents. A campaign is
-- the umbrella: a name, an optional description, an optional date range, an
-- owner and an optional event and ICP. A campaign content is one post or
-- newsletter of the campaign: it has a legal entity (the author), a channel
-- (LinkedIn, X or newsletter), a language, a text and its own status and
-- schedule. Outreaches and relations may point at the campaign they belong to,
-- and CAMPAIGN becomes a source of a relation.
--
-- The enum types are created in the public schema with an otak_ prefix, as in
-- V1_2_100. The language reuses shrine_document_language_enum of V1_2_130.
-- start_date and end_date are calendar dates (DATE) without a time zone, and
-- campaigns_dates_ck keeps the end from lying before the start. planned_at and
-- published_at of a content are instants (TIMESTAMP WITH TIME ZONE): the
-- moment the post is planned to go live and the moment it went live. A
-- SCHEDULED content must have planned_at (campaign_contents_scheduled_ck), and
-- a DRAFT or SCHEDULED content has not been published, so published_at stays
-- empty (campaign_contents_published_ck). Names, descriptions, texts and post
-- URLs have no length cap, so they are TEXT.
--
-- The new relation source value is only added here, never used in this file, so
-- it may share the transaction with the other statements.
--
-- Tenant separation: campaigns_id_organization_id_uk exists only as the target
-- of the composite foreign keys of contents, outreaches and relations, so none
-- can point to a campaign of another organization. The event and ICP foreign
-- keys of a campaign and the legal entity foreign key of a content are
-- composite for the same reason.
--
-- Delete rules:
--   campaigns_organization_id_fk ON DELETE CASCADE: deleting an organization
--     deletes its campaigns.
--   campaigns_event_id_organization_id_fk and
--   campaigns_icp_id_organization_id_fk ON DELETE SET NULL (column): deleting
--     an event or an ICP clears only event_id or icp_id of its campaigns
--     (PostgreSQL 15 and later); the campaigns and their organization_id stay.
--   campaigns_owner_user_id_fk ON DELETE SET NULL: a campaign outlives its
--     owner, as a relation does (relations_owner_user_id_fk).
--   campaign_contents_campaign_id_organization_id_fk ON DELETE CASCADE: the
--     contents of a campaign are deleted with it.
--   campaign_contents_legal_entity_id_organization_id_fk ON DELETE RESTRICT: a
--     legal entity that authored a content cannot be deleted, as for documents
--     (documents_legal_entity_id_organization_id_fk).
--   outreaches_campaign_id_organization_id_fk and
--   relations_campaign_id_organization_id_fk ON DELETE SET NULL (campaign_id):
--     deleting a campaign clears only campaign_id; outreaches and relations
--     stay.
--
-- Indexes:
--   campaigns_organization_id_lower_name_uk is unique per organization on the
--     lower-case name, which enforces the name rule, serves the name search,
--     and serves the organization foreign key and its cascade.
--   campaigns_event_id_idx, campaigns_icp_id_idx and
--   campaigns_owner_user_id_idx serve the foreign keys, the filters by event,
--     ICP and owner, and the clearing of the link on delete.
--   campaign_contents_campaign_id_planned_at_idx serves the contents of one
--     campaign ordered by planned date, the campaign foreign key and its
--     cascade.
--   campaign_contents_legal_entity_id_idx serves the legal entity foreign key,
--     the contents of a legal entity and the RESTRICT check on its delete.
--   outreaches_campaign_id_idx and relations_campaign_id_idx serve the
--     foreign key of the campaign link, the filter by campaign and the
--     clearing of the link when a campaign is deleted.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE otak_campaign_channel_enum AS ENUM ('LINKEDIN', 'X', 'NEWSLETTER');
CREATE TYPE otak_campaign_status_enum AS ENUM ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED');

ALTER TYPE otak_relation_source_enum ADD VALUE IF NOT EXISTS 'CAMPAIGN';

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE otak.campaigns (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    owner_user_id UUID,
    event_id UUID,
    icp_id UUID,
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT campaigns_pk PRIMARY KEY (id),
    CONSTRAINT campaigns_id_organization_id_uk UNIQUE (id, organization_id),
    CONSTRAINT campaigns_dates_ck CHECK ((start_date IS NULL OR end_date IS NULL OR end_date >= start_date)),
    CONSTRAINT campaigns_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT campaigns_event_id_organization_id_fk FOREIGN KEY (event_id, organization_id) REFERENCES otak.events(id, organization_id) ON DELETE SET NULL (event_id),
    CONSTRAINT campaigns_icp_id_organization_id_fk FOREIGN KEY (icp_id, organization_id) REFERENCES otak.icps(id, organization_id) ON DELETE SET NULL (icp_id),
    CONSTRAINT campaigns_owner_user_id_fk FOREIGN KEY (owner_user_id) REFERENCES shrine.users(id) ON DELETE SET NULL
);

CREATE TABLE otak.campaign_contents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    channel otak_campaign_channel_enum NOT NULL,
    language shrine_document_language_enum NOT NULL,
    status otak_campaign_status_enum NOT NULL DEFAULT 'DRAFT',
    text TEXT NOT NULL,
    planned_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    post_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT campaign_contents_pk PRIMARY KEY (id),
    CONSTRAINT campaign_contents_scheduled_ck CHECK ((status <> 'SCHEDULED' OR planned_at IS NOT NULL)),
    CONSTRAINT campaign_contents_published_ck CHECK ((status NOT IN ('DRAFT', 'SCHEDULED') OR published_at IS NULL)),
    CONSTRAINT campaign_contents_campaign_id_organization_id_fk FOREIGN KEY (campaign_id, organization_id) REFERENCES otak.campaigns(id, organization_id) ON DELETE CASCADE,
    CONSTRAINT campaign_contents_legal_entity_id_organization_id_fk FOREIGN KEY (legal_entity_id, organization_id) REFERENCES shrine.legal_entities(id, organization_id) ON DELETE RESTRICT
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE UNIQUE INDEX campaigns_organization_id_lower_name_uk ON otak.campaigns USING btree (organization_id, lower((name)::text));
CREATE INDEX campaigns_event_id_idx ON otak.campaigns USING btree (event_id);
CREATE INDEX campaigns_icp_id_idx ON otak.campaigns USING btree (icp_id);
CREATE INDEX campaigns_owner_user_id_idx ON otak.campaigns USING btree (owner_user_id);
CREATE INDEX campaign_contents_campaign_id_planned_at_idx ON otak.campaign_contents USING btree (campaign_id, planned_at);
CREATE INDEX campaign_contents_legal_entity_id_idx ON otak.campaign_contents USING btree (legal_entity_id);

-- =====================================================
-- 4. Alter outreaches and relations
-- =====================================================

ALTER TABLE otak.outreaches
    ADD COLUMN campaign_id UUID,
    ADD CONSTRAINT outreaches_campaign_id_organization_id_fk FOREIGN KEY (campaign_id, organization_id) REFERENCES otak.campaigns(id, organization_id) ON DELETE SET NULL (campaign_id);

ALTER TABLE otak.relations
    ADD COLUMN campaign_id UUID,
    ADD CONSTRAINT relations_campaign_id_organization_id_fk FOREIGN KEY (campaign_id, organization_id) REFERENCES otak.campaigns(id, organization_id) ON DELETE SET NULL (campaign_id);

CREATE INDEX outreaches_campaign_id_idx ON otak.outreaches USING btree (campaign_id);
CREATE INDEX relations_campaign_id_idx ON otak.relations USING btree (campaign_id);
