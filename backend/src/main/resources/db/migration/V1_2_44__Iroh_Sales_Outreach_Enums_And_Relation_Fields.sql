-- Village Database Schema - Iroh Sales Outreach Enums And Relation Fields
-- V1_2_44__Iroh_Sales_Outreach_Enums_And_Relation_Fields.sql
--
-- Introduces the PostgreSQL ENUM types required by the sales outreach, message,
-- discovery, and appointment modules, and extends iroh.relations with five
-- outreach-tracking columns needed by the automated lead discovery and message
-- flow features.
--
-- New ENUM types:
--   iroh_relation_source_enum         — how a relation was created (manual vs AI)
--   iroh_message_tone_enum            — tone of a sales message template
--   iroh_sales_message_status_enum    — lifecycle state of an outreach message
--   iroh_discovery_run_status_enum    — outcome of a lead discovery scheduler run
--   iroh_appointment_type_enum        — kind of sales appointment
--
-- New columns on iroh.relations:
--   last_outreach_date     — date the most recent outreach message was sent
--   last_response_date     — date the prospect most recently replied
--   no_response_flag_date  — date a sent message was flagged as NO_RESPONSE
--   outreach_count         — total outreach messages sent to this relation
--   source                 — whether this relation was created manually or by AI

-- =====================================================
-- 1. Create enum types
-- =====================================================

-- Tracks whether a relation was created by a human or by the AI discovery job.
CREATE TYPE iroh_relation_source_enum AS ENUM (
    'MANUAL',
    'AI_DISCOVERY'
);

-- Controls the tone Claude uses when selecting and generating an outreach draft.
CREATE TYPE iroh_message_tone_enum AS ENUM (
    'FORMAL',
    'NEUTRAL',
    'FRIENDLY'
);

-- Full lifecycle of a sales outreach message from AI-generated draft to outcome.
CREATE TYPE iroh_sales_message_status_enum AS ENUM (
    'DRAFT',
    'APPROVED',
    'SENT',
    'RESPONDED',
    'NO_RESPONSE',
    'REJECTED'
);

-- Outcome of a single lead discovery scheduler run against one ICP.
CREATE TYPE iroh_discovery_run_status_enum AS ENUM (
    'COMPLETED',
    'FAILED',
    'PARTIAL'
);

-- Classifies sales appointments by interaction type.
CREATE TYPE iroh_appointment_type_enum AS ENUM (
    'CALL',
    'MEETING',
    'DEMO',
    'FOLLOW_UP'
);

-- =====================================================
-- 2. Extend iroh.relations with outreach-tracking columns
-- =====================================================

ALTER TABLE iroh.relations
    ADD COLUMN last_outreach_date    DATE,
    ADD COLUMN last_response_date    DATE,
    ADD COLUMN no_response_flag_date DATE,
    ADD COLUMN outreach_count        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN source                iroh_relation_source_enum;
