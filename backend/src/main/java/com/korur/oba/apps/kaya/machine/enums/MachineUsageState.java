package com.korur.oba.apps.kaya.machine.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Whether the usage limits of a machine are known. NO_ACCOUNT: the machine never reported a Claude
 * account (an API key login) and has no snapshot. NO_SNAPSHOT: it has an account, but no machine of
 * that account reported limits yet.
 */
public enum MachineUsageState {
    KNOWN,
    NO_ACCOUNT,
    NO_SNAPSHOT
}
