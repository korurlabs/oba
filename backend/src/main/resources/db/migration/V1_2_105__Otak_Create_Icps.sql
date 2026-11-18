-- Oba Database Schema - Otak: Create Icps
-- V1_2_105__Otak_Create_Icps.sql
--
-- Adds the ideal customer profile (ICP) of an organization to Otak: the kind of
-- company it wants to win, described by industry, country, company size range
-- and the role of the person to approach. Relations may point at the ICP they
-- were found for. The country of a relation becomes an enum, shared with the
-- ICP.
--
-- Enum types are created in the public schema with an otak_ prefix, as in
-- V1_2_100. otak_country_enum holds the 249 ISO 3166-1 alpha-2 codes of
-- java.util.Locale.getISOCountries on Java 25, in alphabetical order, which is
-- the order of the Java enum. Production has no Otak data, so the type change
-- of otak.relations.country needs no data repair.
--
-- Tenant separation: icps_id_organization_id_uk exists only as the target of
-- the composite foreign key of relations, so a relation can never point to an
-- ICP of another organization.
--
-- Delete rules:
--   icps_organization_id_fk ON DELETE CASCADE: deleting an organization deletes
--     its ICPs.
--   relations_icp_id_organization_id_fk ON DELETE SET NULL (icp_id): deleting
--     an ICP clears only icp_id of its relations (PostgreSQL 15 and later);
--     the relations and their organization_id stay.
--
-- Indexes:
--   icps_organization_id_lower_name_uk is unique per organization on the
--     lower-case name, which enforces the name rule, serves the name order and
--     search, and serves the organization foreign key and its cascade.
--   relations_icp_id_idx serves the foreign key of the ICP link and the clearing
--     of the link when an ICP is deleted.

-- =====================================================
-- 1. Create enums
-- =====================================================

CREATE TYPE otak_industry_enum AS ENUM ('SOFTWARE', 'IT_SERVICES', 'TELECOM', 'FINANCE', 'INSURANCE', 'HEALTHCARE', 'MANUFACTURING', 'LOGISTICS', 'RETAIL', 'ECOMMERCE', 'ENERGY', 'CONSTRUCTION', 'REAL_ESTATE', 'LEGAL', 'ACCOUNTING', 'EDUCATION', 'GOVERNMENT', 'NON_PROFIT', 'HOSPITALITY', 'MEDIA', 'AUTOMOTIVE', 'AGRICULTURE', 'OTHER');
CREATE TYPE otak_target_person_role_enum AS ENUM ('OWNER', 'CEO', 'CTO', 'CIO', 'CISO', 'IT_MANAGER', 'OPERATIONS', 'FINANCE', 'OTHER');
CREATE TYPE otak_country_enum AS ENUM (
    'AD', 'AE', 'AF', 'AG', 'AI', 'AL', 'AM', 'AO', 'AQ', 'AR', 'AS', 'AT',
    'AU', 'AW', 'AX', 'AZ', 'BA', 'BB', 'BD', 'BE', 'BF', 'BG', 'BH', 'BI',
    'BJ', 'BL', 'BM', 'BN', 'BO', 'BQ', 'BR', 'BS', 'BT', 'BV', 'BW', 'BY',
    'BZ', 'CA', 'CC', 'CD', 'CF', 'CG', 'CH', 'CI', 'CK', 'CL', 'CM', 'CN',
    'CO', 'CR', 'CU', 'CV', 'CW', 'CX', 'CY', 'CZ', 'DE', 'DJ', 'DK', 'DM',
    'DO', 'DZ', 'EC', 'EE', 'EG', 'EH', 'ER', 'ES', 'ET', 'FI', 'FJ', 'FK',
    'FM', 'FO', 'FR', 'GA', 'GB', 'GD', 'GE', 'GF', 'GG', 'GH', 'GI', 'GL',
    'GM', 'GN', 'GP', 'GQ', 'GR', 'GS', 'GT', 'GU', 'GW', 'GY', 'HK', 'HM',
    'HN', 'HR', 'HT', 'HU', 'ID', 'IE', 'IL', 'IM', 'IN', 'IO', 'IQ', 'IR',
    'IS', 'IT', 'JE', 'JM', 'JO', 'JP', 'KE', 'KG', 'KH', 'KI', 'KM', 'KN',
    'KP', 'KR', 'KW', 'KY', 'KZ', 'LA', 'LB', 'LC', 'LI', 'LK', 'LR', 'LS',
    'LT', 'LU', 'LV', 'LY', 'MA', 'MC', 'MD', 'ME', 'MF', 'MG', 'MH', 'MK',
    'ML', 'MM', 'MN', 'MO', 'MP', 'MQ', 'MR', 'MS', 'MT', 'MU', 'MV', 'MW',
    'MX', 'MY', 'MZ', 'NA', 'NC', 'NE', 'NF', 'NG', 'NI', 'NL', 'NO', 'NP',
    'NR', 'NU', 'NZ', 'OM', 'PA', 'PE', 'PF', 'PG', 'PH', 'PK', 'PL', 'PM',
    'PN', 'PR', 'PS', 'PT', 'PW', 'PY', 'QA', 'RE', 'RO', 'RS', 'RU', 'RW',
    'SA', 'SB', 'SC', 'SD', 'SE', 'SG', 'SH', 'SI', 'SJ', 'SK', 'SL', 'SM',
    'SN', 'SO', 'SR', 'SS', 'ST', 'SV', 'SX', 'SY', 'SZ', 'TC', 'TD', 'TF',
    'TG', 'TH', 'TJ', 'TK', 'TL', 'TM', 'TN', 'TO', 'TR', 'TT', 'TV', 'TW',
    'TZ', 'UA', 'UG', 'UM', 'US', 'UY', 'UZ', 'VA', 'VC', 'VE', 'VG', 'VI',
    'VN', 'VU', 'WF', 'WS', 'YE', 'YT', 'ZA', 'ZM', 'ZW'
);

-- =====================================================
-- 2. Create tables
-- =====================================================

CREATE TABLE otak.icps (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    industry otak_industry_enum NOT NULL,
    country otak_country_enum,
    company_size_min otak_relation_size_enum,
    company_size_max otak_relation_size_enum,
    target_person_role otak_target_person_role_enum,
    organization_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT icps_pk PRIMARY KEY (id),
    CONSTRAINT icps_id_organization_id_uk UNIQUE (id, organization_id),
    CONSTRAINT icps_company_size_ck CHECK ((company_size_min IS NULL OR company_size_max IS NULL OR company_size_min <= company_size_max)),
    CONSTRAINT icps_description_ck CHECK ((char_length(description) <= 10000)),
    CONSTRAINT icps_organization_id_fk FOREIGN KEY (organization_id) REFERENCES shrine.organizations(id) ON DELETE CASCADE
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

CREATE UNIQUE INDEX icps_organization_id_lower_name_uk ON otak.icps USING btree (organization_id, lower((name)::text));

-- =====================================================
-- 4. Alter relations
-- =====================================================

ALTER TABLE otak.relations
    ALTER COLUMN country TYPE otak_country_enum USING country::otak_country_enum;

ALTER TABLE otak.relations
    ADD COLUMN icp_id UUID,
    ADD CONSTRAINT relations_icp_id_organization_id_fk FOREIGN KEY (icp_id, organization_id) REFERENCES otak.icps(id, organization_id) ON DELETE SET NULL (icp_id);

CREATE INDEX relations_icp_id_idx ON otak.relations USING btree (icp_id);
