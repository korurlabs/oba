package com.korur.oba.apps.kaya.machine.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Status of a machine. A RETIRED machine has no live token, so its recorder gets 401 until the machine
 * is registered again, which makes it ACTIVE with a new token.
 */
public enum MachineStatus {
    ACTIVE,
    RETIRED
}
