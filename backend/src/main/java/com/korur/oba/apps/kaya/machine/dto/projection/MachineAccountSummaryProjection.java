package com.korur.oba.apps.kaya.machine.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The machines whose latest account is one account, in numbers: how many and how many ACTIVE.
 */
public interface MachineAccountSummaryProjection {

    UUID getAccountId();

    Long getMachines();

    Long getActiveMachines();
}
