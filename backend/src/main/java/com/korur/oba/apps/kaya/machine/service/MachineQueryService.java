package com.korur.oba.apps.kaya.machine.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.dto.request.MachineQueryRequest;
import com.korur.oba.apps.kaya.machine.dto.response.MachineAccountSummaryResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineUserSummaryResponse;

/**
 * Reads machines. Other domains use it for the machines of a user, never the machine tables.
 */
public interface MachineQueryService {

    /**
     * Lists the machines of the organization, RETIRED ones included unless filtered, ordered by
     * user display name and machine name.
     *
     * @param organizationId the organization of the request
     * @param request        the optional user, account and status filters
     * @return the machines with their live token
     */
    List<MachineResponse> getMachines(UUID organizationId, MachineQueryRequest request);

    /**
     * Reads one machine of the organization.
     *
     * @param organizationId the organization of the request
     * @param machineId        the machine
     * @return the machine with its live token
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such machine; a machine of another organization answers the same
     */
    MachineResponse getMachine(UUID organizationId, UUID machineId);

    /**
     * Counts the machines of each given user.
     *
     * @param organizationId the organization of the users
     * @param userIds        the users; may be empty
     * @return the summary per user id; a user without machines has no entry
     */
    Map<UUID, MachineUserSummaryResponse> getUserSummaries(UUID organizationId, Collection<UUID> userIds);

    /**
     * Counts the machines whose latest account is each given account.
     *
     * @param organizationId the organization of the accounts
     * @param accountIds     the accounts; may be empty
     * @return the summary per account id; an account without machines has no entry
     */
    Map<UUID, MachineAccountSummaryResponse> getAccountSummaries(UUID organizationId, Collection<UUID> accountIds);

    /**
     * Resolves the machine names of machines of the organization, for lists of another domain that
     * show which machine a row came from.
     *
     * @param organizationId the organization of the machines
     * @param machineIds       the machines; may be empty
     * @return the machine name per machine id; an id of another organization or an unknown id has no entry
     */
    Map<UUID, String> getMachineNames(UUID organizationId, Collection<UUID> machineIds);

    /**
     * Builds the caller of a live machine token, for the token chain. A token outlives the retirement of
     * its machine only as a row: a machine that is no longer ACTIVE may not record or read anything, and
     * neither may the machine of an agent that, or whose membership, is no longer ACTIVE. The organization
     * is not checked here: delivery calls check it with the request's access check and ingest under the
     * machine lock ({@link MachineService#lockActiveMachine}), and both answer a disabled organization
     * with a 403 instead of this 401, so a disable does not make a recorder drop its token.
     *
     * @param organizationId the organization of the token
     * @param machineId      the machine the token was issued to
     * @param tokenId        the token that authenticated the request
     * @return the principal, or empty when the organization has no such machine, the machine is not
     *         ACTIVE, or its user is an agent that, or whose membership, is not ACTIVE
     */
    Optional<MachinePrincipal> findActivePrincipal(UUID organizationId, UUID machineId, UUID tokenId);
}
