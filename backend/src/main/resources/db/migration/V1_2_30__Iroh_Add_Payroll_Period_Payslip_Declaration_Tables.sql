-- Village Database Schema - Iroh Add Payroll Period, Payslip, and Declaration Tables
-- V1_2_28__Iroh_Add_Payroll_Period_Payslip_Declaration_Tables.sql
--
-- Completes the Iroh payroll module schema by introducing:
-- 1. iroh.payroll_periods — payroll calculation cycles (monthly, etc.)
-- 2. iroh.payslips — calculated salary slips per employee per period
-- 3. iroh.payroll_declarations — aggregated tax declarations per period
--
-- Enums: PayrollPeriodType (MONTHLY), PayrollPeriodStatus (DRAFT, PROCESSED, CLOSED)
--        PayslipStatus (DRAFT, FINALIZED, PAID)
--        PayrollDeclarationStatus (DRAFT, SUBMITTED, PAID)
--
-- All tables are soft-deletable via the `deleted` column.
-- All are organization-scoped via `organization_id` FK to shrine.organizations.

-- flyway:executeInTransaction=false

-- =====================================================
-- 1. Create enum types
-- =====================================================

CREATE TYPE iroh_payroll_period_type_enum AS ENUM (
    'MONTHLY'
);

CREATE TYPE iroh_payroll_period_status_enum AS ENUM (
    'DRAFT',
    'PROCESSED',
    'CLOSED'
);

CREATE TYPE iroh_payslip_status_enum AS ENUM (
    'DRAFT',
    'FINALIZED',
    'PAID'
);

CREATE TYPE iroh_payroll_declaration_status_enum AS ENUM (
    'DRAFT',
    'SUBMITTED',
    'PAID'
);

-- =====================================================
-- 2. Create iroh.payroll_periods
-- =====================================================

CREATE TABLE iroh.payroll_periods (
    id                        UUID                                NOT NULL DEFAULT gen_random_uuid(),
    organization_id           UUID                                NOT NULL,
    year                      SMALLINT                            NOT NULL,
    period_number             SMALLINT                            NOT NULL,
    period_type               iroh_payroll_period_type_enum       NOT NULL DEFAULT 'MONTHLY',
    start_date                DATE                                NOT NULL,
    end_date                  DATE                                NOT NULL,
    status                    iroh_payroll_period_status_enum     NOT NULL DEFAULT 'DRAFT',
    created_at                TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT NOW(),
    deleted                   BOOLEAN                             NOT NULL DEFAULT FALSE,
    CONSTRAINT payroll_periods_pk PRIMARY KEY (id),
    CONSTRAINT payroll_periods_organization_id_fk
        FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
        ON DELETE CASCADE,
    CONSTRAINT payroll_periods_date_range_ck
        CHECK (end_date >= start_date),
    CONSTRAINT payroll_periods_period_number_ck
        CHECK (period_number BETWEEN 1 AND 12),
    CONSTRAINT payroll_periods_unique_org_year_period
        UNIQUE (organization_id, year, period_number)
);

-- =====================================================
-- 3. Create iroh.payslips
-- =====================================================

CREATE TABLE iroh.payslips (
    id                              UUID                        NOT NULL DEFAULT gen_random_uuid(),
    organization_id                 UUID                        NOT NULL,
    employee_id                     UUID                        NOT NULL,
    period_id                       UUID                        NOT NULL,
    base_salary                     NUMERIC(15,2)               NOT NULL,
    vacation_pay                    NUMERIC(15,2)               NOT NULL,
    taxable_wage                    NUMERIC(15,2)               NOT NULL,
    income_tax                      NUMERIC(15,2)               NOT NULL,
    health_insurance_premium        NUMERIC(15,2)               NOT NULL,
    net_salary                      NUMERIC(15,2)               NOT NULL,
    worked_hours                    NUMERIC(8,2),
    worked_days                     NUMERIC(5,2),
    cumulative_taxable_wage         NUMERIC(15,2),
    cumulative_income_tax           NUMERIC(15,2),
    cumulative_health_insurance     NUMERIC(15,2),
    cumulative_net_salary           NUMERIC(15,2),
    status                          iroh_payslip_status_enum    NOT NULL DEFAULT 'DRAFT',
    notes                           TEXT,
    created_at                      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    deleted                         BOOLEAN                     NOT NULL DEFAULT FALSE,
    CONSTRAINT payslips_pk PRIMARY KEY (id),
    CONSTRAINT payslips_organization_id_fk
        FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
        ON DELETE CASCADE,
    CONSTRAINT payslips_employee_id_fk
        FOREIGN KEY (employee_id)
        REFERENCES iroh.employees(id)
        ON DELETE RESTRICT,
    CONSTRAINT payslips_period_id_fk
        FOREIGN KEY (period_id)
        REFERENCES iroh.payroll_periods(id)
        ON DELETE RESTRICT,
    CONSTRAINT payslips_unique_employee_period
        UNIQUE (employee_id, period_id)
);

-- =====================================================
-- 4. Create iroh.payroll_declarations
-- =====================================================

CREATE TABLE iroh.payroll_declarations (
    id                      UUID                                    NOT NULL DEFAULT gen_random_uuid(),
    organization_id         UUID                                    NOT NULL,
    period_id               UUID                                    NOT NULL,
    declaration_number      VARCHAR(50),
    contact_person          VARCHAR(100),
    total_wage_lbph         NUMERIC(15,2)                           NOT NULL DEFAULT 0,
    total_income_tax        NUMERIC(15,2)                           NOT NULL DEFAULT 0,
    total_health_insurance  NUMERIC(15,2)                           NOT NULL DEFAULT 0,
    total_to_pay            NUMERIC(15,2)                           NOT NULL DEFAULT 0,
    payment_deadline        DATE,
    payment_reference       VARCHAR(50),
    payment_iban            VARCHAR(34),
    status                  iroh_payroll_declaration_status_enum    NOT NULL DEFAULT 'DRAFT',
    created_at              TIMESTAMP WITH TIME ZONE                NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE                NOT NULL DEFAULT NOW(),
    deleted                 BOOLEAN                                 NOT NULL DEFAULT FALSE,
    CONSTRAINT payroll_declarations_pk PRIMARY KEY (id),
    CONSTRAINT payroll_declarations_organization_id_fk
        FOREIGN KEY (organization_id)
        REFERENCES shrine.organizations(id)
        ON DELETE CASCADE,
    CONSTRAINT payroll_declarations_period_id_fk
        FOREIGN KEY (period_id)
        REFERENCES iroh.payroll_periods(id)
        ON DELETE RESTRICT
);

-- =====================================================
-- 5. Create indexes
-- =====================================================

-- Payroll Periods indexes
CREATE INDEX payroll_periods_organization_id_idx
    ON iroh.payroll_periods(organization_id);

CREATE INDEX payroll_periods_organization_year_idx
    ON iroh.payroll_periods(organization_id, year)
    WHERE deleted = FALSE;

-- Payslips indexes
CREATE INDEX payslips_organization_id_idx
    ON iroh.payslips(organization_id);

CREATE INDEX payslips_employee_id_idx
    ON iroh.payslips(employee_id);

CREATE INDEX payslips_period_id_idx
    ON iroh.payslips(period_id);

CREATE INDEX payslips_organization_status_idx
    ON iroh.payslips(organization_id, status)
    WHERE deleted = FALSE;

-- Payroll Declarations indexes
CREATE INDEX payroll_declarations_organization_id_idx
    ON iroh.payroll_declarations(organization_id);

CREATE INDEX payroll_declarations_period_id_idx
    ON iroh.payroll_declarations(period_id);

CREATE INDEX payroll_declarations_organization_status_idx
    ON iroh.payroll_declarations(organization_id, status)
    WHERE deleted = FALSE;
