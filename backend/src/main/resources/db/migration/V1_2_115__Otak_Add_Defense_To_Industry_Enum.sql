-- Oba Database Schema - Otak: Add DEFENSE To Industry Enum
-- V1_2_115__Otak_Add_Defense_To_Industry_Enum.sql
--
-- Extends otak_industry_enum with DEFENSE so that an ICP can target defence
-- manufacturers and suppliers, which MANUFACTURING and OTHER describe too loosely.
-- Placed before OTHER to keep the order of the Java enum Industry.
-- Alone in its file: a new enum value cannot be used in the transaction that adds it.

ALTER TYPE otak_industry_enum ADD VALUE IF NOT EXISTS 'DEFENSE' BEFORE 'OTHER';
