-- Village Database Schema - Iroh Fix Vat Category Enum
-- V1_2_55__Iroh_Fix_Vat_Category_Enum.sql
--
-- Replaces the stale iroh_vat_category_enum PostgreSQL type with the current Java enum values.
--
-- Root cause: the Java VatCategory enum was renamed wholesale after V1_2_6 — 6 of 8 values were
-- renamed (e.g. STANDARD_HIGH -> DOMESTIC_HIGH) and 3 new values were added — but no Flyway
-- migration was created to update the DB type. This caused inserts to fail with
-- "invalid input value for enum iroh_vat_category_enum" for any of the renamed values.
--
-- The iroh.invoice_line_items table is empty (confirmed by the user), so no row-level UPDATE
-- statements are needed before the column type cast.
--
-- Pattern: rename old type aside, create new type, cast column, drop old type.
-- The original type in V1_2_6 was created unqualified, so it lives in the default
-- search_path schema (public). References here remain unqualified to match.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Rename old enum type aside
-- =====================================================

ALTER TYPE iroh_vat_category_enum RENAME TO iroh_vat_category_enum_old;

-- =====================================================
-- 2. Create new enum type with all 11 Java values
-- =====================================================

CREATE TYPE iroh_vat_category_enum AS ENUM (
    'DOMESTIC_HIGH',
    'DOMESTIC_LOW',
    'DOMESTIC_ZERO',
    'EXPORT_OUTSIDE_EU',
    'EU_B2B_SUPPLY',
    'EU_B2C_DISTANCE_SALE',
    'DOMESTIC_REVERSE_CHARGE',
    'EU_PURCHASE_REVERSE_CHARGE',
    'NON_EU_PURCHASE_REVERSE_CHARGE',
    'EXEMPT',
    'NOT_APPLICABLE'
);

-- =====================================================
-- 3. Migrate column to new type
-- =====================================================

-- Table is empty — the USING cast is a schema-level type substitution only.
-- If any unexpected rows carry old values (e.g. STANDARD_HIGH), PostgreSQL will
-- fail immediately here rather than silently accepting invalid data.
-- Note: the table is iroh-schema-qualified; the type is unqualified (lives in public).
ALTER TABLE iroh.invoice_line_items
    ALTER COLUMN vat_category TYPE iroh_vat_category_enum
    USING vat_category::text::iroh_vat_category_enum;

-- =====================================================
-- 4. Drop old enum type
-- =====================================================

DROP TYPE iroh_vat_category_enum_old;
