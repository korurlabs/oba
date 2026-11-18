-- Database Schema - Shrine: Add EXPO_PUSH To Connect Retry System Enum
-- V1_2_104__Shrine_Add_Expo_Push_Retry_System.sql
--
-- Extends shrine_connect_retry_system_enum with the EXPO_PUSH value so that
-- failed Expo push calls can be stored as shrine.retry_events and
-- shrine.retry_dead_events rows (ConnectRetrySystem.EXPO_PUSH).
-- Uses IF NOT EXISTS to make the migration safely re-runnable.

ALTER TYPE shrine_connect_retry_system_enum ADD VALUE IF NOT EXISTS 'EXPO_PUSH';
