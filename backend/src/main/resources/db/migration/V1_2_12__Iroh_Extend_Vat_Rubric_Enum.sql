-- Village Database Schema - Iroh Extend Vat Rubric Enum
-- V1_2_10__Iroh_Extend_Vat_Rubric_Enum.sql
--
-- Adds missing BTW return rubrieken to the iroh_vat_rubric_enum type.
-- R_1D (private use), R_5A through R_5G (summary/calculation boxes)
-- are needed by VatReturnGenerationService to store calculated lines.

-- flyway:executeInTransaction=false

ALTER TYPE iroh_vat_rubric_enum ADD VALUE IF NOT EXISTS 'R_1D';
ALTER TYPE iroh_vat_rubric_enum ADD VALUE IF NOT EXISTS 'R_5A';
ALTER TYPE iroh_vat_rubric_enum ADD VALUE IF NOT EXISTS 'R_5C';
ALTER TYPE iroh_vat_rubric_enum ADD VALUE IF NOT EXISTS 'R_5D';
ALTER TYPE iroh_vat_rubric_enum ADD VALUE IF NOT EXISTS 'R_5E';
ALTER TYPE iroh_vat_rubric_enum ADD VALUE IF NOT EXISTS 'R_5F';
ALTER TYPE iroh_vat_rubric_enum ADD VALUE IF NOT EXISTS 'R_5G';
