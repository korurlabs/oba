-- Oba Database Schema - Otak: Pipeline Stage Closed And Direct
-- V1_2_107__Otak_Pipeline_Stage_Closed_And_Direct.sql
--
-- A won lead stays on the pipeline as a customer, so a customer now has a stage
-- too. The lead stage enum is replaced by otak_pipeline_stage_enum with two new
-- values: CLOSED (a lead that was won and became a customer) and DIRECT (a
-- customer that never went through the pipeline). The declaration order is the
-- order of the board columns, as PostgreSQL sorts an enum by declaration order:
--
--   NEW, QUALIFIED, CONTACTED, WARM, PROPOSAL, CLOSED, LOST, PARKED, DIRECT
--
-- The enum is recreated and not extended with ADD VALUE, because a value added
-- in a transaction cannot be used in the same transaction, and Flyway runs this
-- file in one transaction.
--
-- Existing customers had no stage. They become DIRECT, since no pipeline history
-- was recorded for them. Leads keep their stage, suppliers keep a null stage.
--
-- relations_stage_ck is replaced by a rule per type:
--   LEAD: one of the seven lead stages
--   CUSTOMER: CLOSED or DIRECT
--   SUPPLIER: no stage
-- The explicit IS NOT NULL matters: a check passes when its expression is NULL,
-- and NULL IN (...) is NULL, so without it a lead or customer could have no stage.
--
-- Indexes: relations_organization_id_type_stage_idx is kept. PostgreSQL rebuilds
-- it when the column type changes; it still serves the list tabs, the stage
-- filter and the board.
--
-- Delete rules: none change.

-- =====================================================
-- 1. Drop the old constraint
-- =====================================================

ALTER TABLE otak.relations
    DROP CONSTRAINT relations_stage_ck;

-- =====================================================
-- 2. Recreate the enum
-- =====================================================

ALTER TYPE otak_lead_stage_enum RENAME TO otak_lead_stage_enum_old;

CREATE TYPE otak_pipeline_stage_enum AS ENUM ('NEW', 'QUALIFIED', 'CONTACTED', 'WARM', 'PROPOSAL', 'CLOSED', 'LOST', 'PARKED', 'DIRECT');

-- =====================================================
-- 3. Change the column type and set existing customers to DIRECT
-- =====================================================

ALTER TABLE otak.relations
    ALTER COLUMN stage TYPE otak_pipeline_stage_enum
    USING CASE
        WHEN type = 'CUSTOMER'::otak_relation_type_enum THEN 'DIRECT'::otak_pipeline_stage_enum
        ELSE stage::text::otak_pipeline_stage_enum
    END;

-- =====================================================
-- 4. Drop the old enum
-- =====================================================

DROP TYPE otak_lead_stage_enum_old;

-- =====================================================
-- 5. Add the new constraint
-- =====================================================

ALTER TABLE otak.relations
    ADD CONSTRAINT relations_stage_ck CHECK (
        (type = 'LEAD'::otak_relation_type_enum
            AND stage IS NOT NULL
            AND stage IN ('NEW', 'QUALIFIED', 'CONTACTED', 'WARM', 'PROPOSAL', 'LOST', 'PARKED'))
        OR (type = 'CUSTOMER'::otak_relation_type_enum
            AND stage IS NOT NULL
            AND stage IN ('CLOSED', 'DIRECT'))
        OR (type = 'SUPPLIER'::otak_relation_type_enum
            AND stage IS NULL)
    );
