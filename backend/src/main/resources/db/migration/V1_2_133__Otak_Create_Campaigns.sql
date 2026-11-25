-- Oba Database Schema - Otak: Create Campaigns
-- V1_2_133__Otak_Create_Campaigns.sql
--
-- Adds the campaign of an organization to Otak: a post or newsletter on one
-- channel (LinkedIn, X or newsletter) with its text per language. Outreaches
-- and relations may point at the campaign they belong to, and CAMPAIGN becomes
-- a source of a relation.
--
-- The enum types are created in the public schema with an otak_ prefix, as in
-- V1_2_100. The language reuses shrine_document_language_enum of V1_2_130.
-- planned_at and published_at are instants (TIMESTAMP WITH TIME ZONE): the
-- moment the post is planned to go live and the moment it went live. A
-- SCHEDULED campaign must have planned_at (campaigns_scheduled_ck), and a DRAFT
-- or SCHEDULED campaign has not been published, so published_at stays empty
-- (campaigns_published_ck).
--
-- The new relation source value is only added here, never used in this file, so
-- it may share the transaction with the other statements.
--
-- Tenant separation: campaigns_id_organization_id_uk exists only as the target
-- of the composite foreign keys of outreaches and relations, so neither can
-- point to a campaign of another organization. The event and ICP foreign keys
-- of a campaign are composite for the same reason.
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
--   campaign_contents_campaign_id_fk ON DELETE CASCADE: the texts of a
--     campaign are deleted with it.
--   outreaches_campaign_id_organization_id_fk and
--   relations_campaign_id_organization_id_fk ON DELETE SET NULL (campaign_id):
--     deleting a campaign clears only campaign_id; outreaches and relations
--     stay.
--
-- Indexes:
--   campaigns_organization_id_lower_name_uk is unique per organization on the
--     lower-case name, which enforces the name rule, serves the name search,
--     and serves the organization foreign key and its cascade.
--   campaigns_organization_id_status_planned_at_idx serves the list tabs per
--     status and the upcoming scheduled campaigns, ordered by planned date, for
--     one organization.
--   campaigns_event_id_idx, campaigns_icp_id_idx and
--   campaigns_owner_user_id_idx serve the foreign keys, the filters by event,
--     ICP and owner, and the clearing of the link on delete.
--   campaign_contents_campaign_id_language_uk is unique per campaign and
--     language, and also serves the campaign foreign key and its cascade.
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
    name VARCHAR(200) NOT NULL,
    channel otak_campaign_channel_enum NOT NULL,
    status otak_campaign_status_enum NOT NULL DEFAULT 'DRAFT',
    planned_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    post_url VARCHAR(500),
    owner_user_id UUID,
    event_id UUID,
    icp_id UUID,
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT campaigns_pk PRIMARY KEY (id),
    CONSTRAINT campaigns_id_organization_id_uk UNIQUE (id, organization_id),
    CONSTRAINT campaigns_scheduled_ck CHECK ((status <> 'SCHEDULED' OR planned_at IS NOT NULL)),
    CONSTRAINT campaigns_published_ck CHECK ((status NOT IN ('DRAFT', 'SCHEDULED') OR published_at IS NULL)),
    CONSTRAINT campaigns_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE,
    CONSTRAINT campaigns_event_id_organization_id_fk FOREIGN KEY (event_id, organization_id) REFERENCES otak.events(id, organization_id) ON DELETE SET NULL (event_id),
    CONSTRAINT campaigns_icp_id_organization_id_fk FOREIGN KEY (icp_id, organization_id) REFERENCES otak.icps(id, organization_id) ON DELETE SET NULL (icp_id),
    CONSTRAINT campaigns_owner_user_id_fk FOREIGN KEY (owner_user_id) REFERENCES shrine.users(id) ON DELETE SET NULL
);

CREATE TABLE otak.campaign_contents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL,
    language shrine_document_language_enum NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT campaign_contents_pk PRIMARY KEY (id),
    CONSTRAINT campaign_contents_campaign_id_fk FOREIGN KEY (campaign_id) REFERENCES otak.campaigns(id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE UNIQUE INDEX campaigns_organization_id_lower_name_uk ON otak.campaigns USING btree (organization_id, lower((name)::text));
CREATE INDEX campaigns_organization_id_status_planned_at_idx ON otak.campaigns USING btree (organization_id, status, planned_at);
CREATE INDEX campaigns_event_id_idx ON otak.campaigns USING btree (event_id);
CREATE INDEX campaigns_icp_id_idx ON otak.campaigns USING btree (icp_id);
CREATE INDEX campaigns_owner_user_id_idx ON otak.campaigns USING btree (owner_user_id);
CREATE UNIQUE INDEX campaign_contents_campaign_id_language_uk ON otak.campaign_contents USING btree (campaign_id, language);

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
