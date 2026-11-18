package com.korur.oba.apps.kaya.machine.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.response.embedded.MachineUsageResponse;
import com.korur.oba.apps.kaya.machine.entity.Machine;

/**
 * Reads the subscription usage limits of machines. Limits belong to an account, so a machine
 * with an account shows the account's newest snapshot across the organization's machines, and an
 * machine without one shows its own snapshot.
 */
public interface MachineUsageQueryService {

    /**
     * The usage of one machine.
     *
     * @param machine the machine, with its latest account
     * @param now   the moment the windows are judged against: a window whose reset time is at or
     *              before it is shown reset, without a percentage
     * @return the usage, KNOWN or the reason it is unknown
     */
    MachineUsageResponse usageFor(Machine machine, Instant now);

    /**
     * The usage of several machines of one organization, in a fixed number of queries.
     *
     * @param organizationId the organization of the machines
     * @param machines         the machines; may be empty
     * @param now            the moment the windows are judged against
     * @return the usage per machine id, one entry for every machine
     */
    Map<UUID, MachineUsageResponse> usageFor(UUID organizationId, List<Machine> machines, Instant now);

    /**
     * The usage of several accounts of one organization: each account's newest snapshot across the
     * organization's machines, in one query. The usage belongs to the account rather than to one
     * machine, so no machine shares it and sharedWith is empty.
     *
     * @param organizationId the organization of the accounts
     * @param accountIds     the accounts; may be empty
     * @param now            the moment the windows are judged against
     * @return the usage per account id, KNOWN or NO_SNAPSHOT, one entry for every account
     */
    Map<UUID, MachineUsageResponse> usageForAccounts(UUID organizationId, Collection<UUID> accountIds, Instant now);
}
