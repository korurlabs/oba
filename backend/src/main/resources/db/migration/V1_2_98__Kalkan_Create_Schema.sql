-- Oba Database Schema - Kalkan: Create Schema
-- V1_2_98__Kalkan_Create_Schema.sql
--
-- Adds the Kalkan documentation app to the platform database: schema kalkan with
-- pages, page translations, page permissions and page revisions.
--
-- Nothing is erased. Pages, translations and permissions are soft-deleted
-- (deleted, created_by_id, updated_by_id), so a deleted row still shows who
-- deleted it and when. Page revisions are append-only: no deleted column, and no
-- code updates or deletes them. No foreign key cascades: organizations and users
-- are soft-deleted themselves, and no row may vanish with a parent.
--
-- Enum types are created in the public schema, as the other apps of this set do.
-- Partial unique indexes WHERE deleted = FALSE free a deleted row's key for reuse.

-- =====================================================
-- 1. Create schema
-- =====================================================

CREATE SCHEMA IF NOT EXISTS kalkan;

-- =====================================================
-- 2. Create enums
-- =====================================================

CREATE TYPE page_page_translation_locale_enum AS ENUM ('TR', 'EN');
CREATE TYPE permission_page_permission_level_enum AS ENUM ('VIEW', 'EDIT', 'MANAGE');

-- =====================================================
-- 3. Create tables
-- =====================================================

CREATE TABLE kalkan.pages (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    parent_id UUID,
    root_id UUID,
    slug VARCHAR(100) NOT NULL,
    position INTEGER NOT NULL,
    compliance BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    pushed_version BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_id UUID NOT NULL,
    updated_by_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pages_pk PRIMARY KEY (id),
    CONSTRAINT pages_slug_ck CHECK (slug ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$'),
    CONSTRAINT pages_root_ck CHECK ((parent_id IS NULL) = (root_id IS NULL)),
    CONSTRAINT pages_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id),
    CONSTRAINT pages_parent_id_fk FOREIGN KEY (parent_id) REFERENCES kalkan.pages(id),
    CONSTRAINT pages_root_id_fk FOREIGN KEY (root_id) REFERENCES kalkan.pages(id),
    CONSTRAINT pages_created_by_id_fk FOREIGN KEY (created_by_id) REFERENCES shrine.users(id),
    CONSTRAINT pages_updated_by_id_fk FOREIGN KEY (updated_by_id) REFERENCES shrine.users(id)
);

CREATE TABLE kalkan.page_translations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    page_id UUID NOT NULL,
    locale page_page_translation_locale_enum NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_id UUID NOT NULL,
    updated_by_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT page_translations_pk PRIMARY KEY (id),
    CONSTRAINT page_translations_body_ck CHECK (char_length(body) <= 500000),
    CONSTRAINT page_translations_page_id_fk FOREIGN KEY (page_id) REFERENCES kalkan.pages(id),
    CONSTRAINT page_translations_created_by_id_fk FOREIGN KEY (created_by_id) REFERENCES shrine.users(id),
    CONSTRAINT page_translations_updated_by_id_fk FOREIGN KEY (updated_by_id) REFERENCES shrine.users(id)
);

CREATE TABLE kalkan.page_permissions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    page_id UUID NOT NULL,
    user_id UUID,
    level permission_page_permission_level_enum NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_id UUID NOT NULL,
    updated_by_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT page_permissions_pk PRIMARY KEY (id),
    CONSTRAINT page_permissions_subject_ck CHECK (user_id IS NOT NULL),
    CONSTRAINT page_permissions_page_id_fk FOREIGN KEY (page_id) REFERENCES kalkan.pages(id),
    CONSTRAINT page_permissions_user_id_fk FOREIGN KEY (user_id) REFERENCES shrine.users(id),
    CONSTRAINT page_permissions_created_by_id_fk FOREIGN KEY (created_by_id) REFERENCES shrine.users(id),
    CONSTRAINT page_permissions_updated_by_id_fk FOREIGN KEY (updated_by_id) REFERENCES shrine.users(id)
);

CREATE TABLE kalkan.page_revisions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    page_id UUID NOT NULL,
    locale page_page_translation_locale_enum NOT NULL,
    page_version BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    created_by_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT page_revisions_pk PRIMARY KEY (id),
    CONSTRAINT page_revisions_body_ck CHECK (char_length(body) <= 500000),
    CONSTRAINT page_revisions_page_id_fk FOREIGN KEY (page_id) REFERENCES kalkan.pages(id),
    CONSTRAINT page_revisions_created_by_id_fk FOREIGN KEY (created_by_id) REFERENCES shrine.users(id)
);

-- =====================================================
-- 4. Create unique indexes (live rows only)
-- =====================================================

-- A root page (null parent) counts as one parent value, so root slugs are unique per organization.
CREATE UNIQUE INDEX pages_organization_id_parent_id_slug_uk ON kalkan.pages (organization_id, parent_id, slug) NULLS NOT DISTINCT WHERE deleted = FALSE;
CREATE UNIQUE INDEX page_translations_page_id_locale_uk ON kalkan.page_translations (page_id, locale) WHERE deleted = FALSE;
CREATE UNIQUE INDEX page_permissions_page_id_user_id_uk ON kalkan.page_permissions (page_id, user_id) WHERE deleted = FALSE;

-- =====================================================
-- 5. Create indexes
-- =====================================================

-- Live-row filter of every soft-deleted table.
CREATE INDEX pages_deleted_idx ON kalkan.pages (deleted) WHERE deleted = FALSE;
CREATE INDEX page_translations_deleted_idx ON kalkan.page_translations (deleted) WHERE deleted = FALSE;
CREATE INDEX page_permissions_deleted_idx ON kalkan.page_permissions (deleted) WHERE deleted = FALSE;

-- Foreign keys. The unique indexes above are partial and do not serve a lookup that includes deleted rows.
CREATE INDEX pages_organization_id_idx ON kalkan.pages (organization_id);
CREATE INDEX pages_parent_id_idx ON kalkan.pages (parent_id);
CREATE INDEX pages_root_id_idx ON kalkan.pages (root_id);
CREATE INDEX pages_created_by_id_idx ON kalkan.pages (created_by_id);
CREATE INDEX pages_updated_by_id_idx ON kalkan.pages (updated_by_id);

CREATE INDEX page_translations_page_id_idx ON kalkan.page_translations (page_id);
CREATE INDEX page_translations_created_by_id_idx ON kalkan.page_translations (created_by_id);
CREATE INDEX page_translations_updated_by_id_idx ON kalkan.page_translations (updated_by_id);

CREATE INDEX page_permissions_page_id_idx ON kalkan.page_permissions (page_id);
CREATE INDEX page_permissions_user_id_idx ON kalkan.page_permissions (user_id);
CREATE INDEX page_permissions_created_by_id_idx ON kalkan.page_permissions (created_by_id);
CREATE INDEX page_permissions_updated_by_id_idx ON kalkan.page_permissions (updated_by_id);

-- The history of one page and locale; its leading column also serves the page_id foreign key.
CREATE INDEX page_revisions_page_id_locale_idx ON kalkan.page_revisions (page_id, locale);
CREATE INDEX page_revisions_created_by_id_idx ON kalkan.page_revisions (created_by_id);
