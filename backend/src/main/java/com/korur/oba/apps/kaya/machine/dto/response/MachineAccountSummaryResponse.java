package com.korur.oba.apps.kaya.machine.dto.response;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The machines of one account in numbers, for the account list and detail. A machine counts for the
 * account it reported last, so a machine that moved to another account counts only there.
 *
 * @param accountId    the account
 * @param machines       all machines on the account, RETIRED ones included
 * @param activeMachines the ACTIVE machines on the account
 */
public record MachineAccountSummaryResponse(
        UUID accountId,
        long machines,
        long activeMachines
) {
}
