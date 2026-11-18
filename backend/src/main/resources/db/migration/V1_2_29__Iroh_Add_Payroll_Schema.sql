-- Village Database Schema - Iroh Add Payroll Schema
-- V1_2_27__Iroh_Add_Payroll_Schema.sql
--
-- Introduces the employee master data table for the Iroh payroll module.
-- This migration creates only the employee table; payroll periods, payslips
-- and payroll declarations are introduced in later migrations.
--
-- 1. Creates the iroh_payroll_tax_table_enum PostgreSQL enum (WHITE, GREEN).
-- 2. Creates the iroh_employee_status_enum PostgreSQL enum (ACTIVE, INACTIVE).
-- 3. Creates the iroh.employees table organization-scoped via shrine.organizations.
--
-- Soft-delete is implemented via the `deleted BOOLEAN NOT NULL DEFAULT FALSE`
-- column on the table, consistent with the SoftDelete strategy used elsewhere
-- in the Iroh domain.
--
-- flyway:executeInTransaction=false is required because PostgreSQL's
-- CREATE TYPE / ALTER TYPE cannot run inside a transaction in some scenarios
-- and is kept here for consistency with sibling Iroh migrations.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE iroh_payroll_tax_table_enum AS ENUM (
    'WHITE',
    'GREEN'
);

CREATE TYPE iroh_employee_status_enum AS ENUM (
    'ACTIVE',
    'INACTIVE'
);

-- =====================================================
-- 2. Create iroh.employees
-- =====================================================

CREATE TABLE iroh.employees (
    id                        UUID                          NOT NULL DEFAULT gen_random_uuid(),
    organization_id           UUID                          NOT NULL,
    first_name                VARCHAR(100)                  NOT NULL,
    last_name                 VARCHAR(100)                  NOT NULL,
    bsn                       VARCHAR(9),
    date_of_birth             DATE,
    employment_start_date     DATE                          NOT NULL,
    employment_end_date       DATE,
    function                  VARCHAR(255),
    department                VARCHAR(255),
    base_salary               NUMERIC(15,2)                 NOT NULL DEFAULT 0,
    hourly_rate               NUMERIC(15,2),
    hours_per_week            NUMERIC(5,2)                  NOT NULL DEFAULT 40,
    part_time_percentage      INTEGER                       NOT NULL DEFAULT 100,
    iban                      VARCHAR(34),
    payroll_tax_table         iroh_payroll_tax_table_enum   NOT NULL DEFAULT 'WHITE',
    tax_credit_applied        BOOLEAN                       NOT NULL DEFAULT TRUE,
    socially_insured          BOOLEAN                       NOT NULL DEFAULT TRUE,
    status                    iroh_employee_status_enum     NOT NULL DEFAULT 'ACTIVE',
    created_at                TIMESTAMP WITH TIME ZONE      NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE      NOT NULL DEFAULT NOW(),
    deleted                   BOOLEAN                       NOT NULL DEFAULT FALSE,
    CONSTRAINT employees_pk PRIMARY KEY (id),
    CONSTRAINT employees_organization_id_fk
        FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
        ON DELETE CASCADE,
    CONSTRAINT employees_employment_dates_ck
        CHECK (employment_end_date IS NULL OR employment_end_date >= employment_start_date),
    CONSTRAINT employees_part_time_percentage_ck
        CHECK (part_time_percentage BETWEEN 0 AND 100)
);

-- =====================================================
-- 3. Create indexes
-- =====================================================

-- FK support — every FK must be indexed.
CREATE INDEX employees_organization_id_idx
    ON iroh.employees(organization_id);

-- List view filter: active employees per organization.
CREATE INDEX employees_organization_id_status_idx
    ON iroh.employees(organization_id, status)
    WHERE deleted = FALSE;

-- Search by name within an organization.
CREATE INDEX employees_organization_id_last_name_idx
    ON iroh.employees(organization_id, last_name)
    WHERE deleted = FALSE;
