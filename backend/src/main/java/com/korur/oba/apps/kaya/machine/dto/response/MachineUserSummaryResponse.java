package com.korur.oba.apps.kaya.machine.dto.response;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The machines of one user in numbers, for the user list and detail.
 *
 * @param userId       the user
 * @param machines       all machines of the user, RETIRED ones included
 * @param activeMachines the ACTIVE machines
 * @param lastSeenAt   when one of the machines was last seen; null when none was
 */
public record MachineUserSummaryResponse(
        UUID userId,
        long machines,
        long activeMachines,
        Instant lastSeenAt
) {
}
