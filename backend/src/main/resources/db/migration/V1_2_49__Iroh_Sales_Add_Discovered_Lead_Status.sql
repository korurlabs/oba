-- Village Database Schema - Iroh Sales: Add DISCOVERED Lead Status
-- V1_2_49__Iroh_Sales_Add_Discovered_Lead_Status.sql
--
-- Adds the DISCOVERED value to iroh_lead_status_enum so that leads created by the
-- AI lead-discovery scheduler can be persisted with a distinct status that precedes
-- FIRST_CONTACT in the funnel. The order is enforced via BEFORE 'FIRST_CONTACT' so
-- iroh_lead_status_enum's declaration order matches the LeadStatus Java enum.
--
-- iroh_lead_status_enum is a native PostgreSQL ENUM type (defined in
-- V1_2_21__Iroh_Create_Sales_Infrastructure.sql). ALTER TYPE ... ADD VALUE is the
-- correct approach; no DROP/RECREATE cycle is needed.
--
-- flyway:executeInTransaction=false is required because ALTER TYPE ADD VALUE
-- cannot run inside a transaction in PostgreSQL.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Add DISCOVERED to iroh_lead_status_enum
-- =====================================================

-- Inserts DISCOVERED before FIRST_CONTACT in declaration order.
-- IF NOT EXISTS guards against accidental double-application.
ALTER TYPE iroh_lead_status_enum ADD VALUE IF NOT EXISTS 'DISCOVERED' BEFORE 'FIRST_CONTACT';
