-- Database Schema - Invoice Account Snapshot & Currency Support
-- V1_2_4__Add_Invoice_Account_Snapshot_And_Currency.sql
--
-- Adds account snapshot columns to preserve account details at invoice creation time,
-- and currency/exchange rate columns for multi-currency support.

-- =====================================================
-- 1. Account snapshot columns
-- =====================================================

ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_name        VARCHAR(255);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_vat_number  VARCHAR(20);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_kvk_number  VARCHAR(20);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_email       VARCHAR(255);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_phone       VARCHAR(50);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_website     VARCHAR(500);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_iban        VARCHAR(34);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_address     VARCHAR(500);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_postal_code VARCHAR(10);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_city        VARCHAR(100);
ALTER TABLE iroh.invoices ADD COLUMN snapshot_account_country     VARCHAR(100);

-- =====================================================
-- 2. Currency and exchange rate
-- =====================================================

ALTER TABLE iroh.invoices ADD COLUMN currency       VARCHAR(3)     NOT NULL DEFAULT 'EUR';
ALTER TABLE iroh.invoices ADD COLUMN exchange_rate   NUMERIC(12,6)  NOT NULL DEFAULT 1.000000;
ALTER TABLE iroh.invoices ADD COLUMN base_currency   VARCHAR(3)     NOT NULL DEFAULT 'EUR';
