package com.korur.oba.apps.kaya.machine.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The machines of one user in numbers: how many, how many ACTIVE, and when one was last seen.
 */
public interface MachineUserSummaryProjection {

    UUID getUserId();

    Long getMachines();

    Long getActiveMachines();

    Instant getLastSeenAt();
}
