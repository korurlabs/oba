-- Oba Database Schema - Shrine: Create Legal Entities
-- V1_2_129__Shrine_Create_Legal_Entities.sql
--
-- A legal entity is a company of an organization that signs documents, for
-- example "Korur B.V." and "Korur Siber Guvenlik Limited Sirketi" in the one
-- organization Korur. short_name is the label shown in pickers, legal_name is
-- the name printed in a contract. governing_law and court hold what the
-- company's contracts name.
--
-- Rows are removed for good (no soft delete column), as BaseEntity has none.
--
-- Delete rules:
--   legal_entities_organization_id_fk ON DELETE CASCADE: deleting an
--     organization deletes its legal entities, as it already deletes the
--     relations and templates that belong to it.
--
-- Indexes and constraints:
--   legal_entities_organization_id_lower_short_name_uk: a short name is unique
--     in an organization, whatever its case, so a picker never shows two equal
--     labels. It starts with organization_id, so it also serves the foreign key
--     lookup and the cascade, and no separate index on organization_id is made.
--   legal_entities_id_organization_id_uk: id is already unique, so this adds no
--     rule of its own. It is the target of composite foreign keys
--     (legal_entity_id, organization_id) of later tables, which make it
--     impossible for a row to name a legal entity of another organization.

CREATE TABLE shrine.legal_entities (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    short_name VARCHAR(100) NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    country VARCHAR(3) NOT NULL,
    registration_number VARCHAR(50) NULL,
    vat_number VARCHAR(50) NULL,
    street VARCHAR(200) NULL,
    postal_code VARCHAR(20) NULL,
    city VARCHAR(100) NULL,
    governing_law VARCHAR(200) NULL,
    court VARCHAR(200) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT legal_entities_pk PRIMARY KEY (id),
    CONSTRAINT legal_entities_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations (id) ON DELETE CASCADE,
    CONSTRAINT legal_entities_id_organization_id_uk UNIQUE (id, organization_id)
);

CREATE UNIQUE INDEX legal_entities_organization_id_lower_short_name_uk
    ON shrine.legal_entities (organization_id, lower(short_name));
