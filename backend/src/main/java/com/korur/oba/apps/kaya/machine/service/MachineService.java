package com.korur.oba.apps.kaya.machine.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.dto.request.MachineCreateRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUpdateRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUsageSnapshotRequest;
import com.korur.oba.apps.kaya.machine.dto.response.MachineCreatedResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineResponse;
import com.korur.oba.apps.kaya.machine.dto.response.MachineTokenCreatedResponse;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations on machines and their tokens. Every user action writes one audit entry on the
 * machine's user in the same transaction; revoke all of a user leaves the entry to its caller,
 * which writes one for the whole action.
 */
public interface MachineService {

    /**
     * Registers a machine for a user and issues its first token, audited as MACHINE_CREATED. A
     * RETIRED machine of the user with the same machine name is made ACTIVE again with a new
     * token instead, so a machine can record again after a revoke all.
     *
     * @param actor          the signed-in user; ADMIN when the machine is for another user
     * @param organizationId the organization of the request
     * @param request        machine name and the optional user
     * @return the machine and its plaintext token, shown only here
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_VAL_006 when the
     *         user has an ACTIVE machine with this machine name
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         user is not a member of the organization
     */
    MachineCreatedResponse createMachine(User actor, UUID organizationId, MachineCreateRequest request);

    /**
     * Issues a new token to a machine and revokes its live one in the same transaction, audited as
     * TOKEN_ROTATED, or TOKEN_ISSUED when the machine had no live token. Only the machine's own user
     * may do this, because only they can put the token on the machine. An agent's machine is rotated
     * by an ADMIN instead, because an agent never mints a credential.
     *
     * @param actor          the user of the machine, or an ADMIN when that user is an agent
     * @param organizationId the organization of the request
     * @param machineId        the machine
     * @return the new plaintext token, shown only here
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_VAL_011 when the
     *         machine is RETIRED
     */
    MachineTokenCreatedResponse rotateToken(User actor, UUID organizationId, UUID machineId);

    /**
     * Revokes the live token of a machine, audited as TOKEN_REVOKED. A machine without a live token
     * is left as it is, without an entry.
     *
     * @param actor          an ADMIN or the user of the machine
     * @param organizationId the organization of the request
     * @param machineId        the machine
     */
    void revokeToken(User actor, UUID organizationId, UUID machineId);

    /**
     * Retires a machine and revokes its live token, audited as MACHINE_RETIRED. A RETIRED machine is
     * returned as it is, without an entry.
     *
     * @param actor          an ADMIN or the user of the machine
     * @param organizationId the organization of the request
     * @param machineId        the machine
     * @param request        the new status, RETIRED
     * @return the machine
     */
    MachineResponse updateMachine(User actor, UUID organizationId, UUID machineId, MachineUpdateRequest request);

    /**
     * Retires every ACTIVE machine of a user in one organization and revokes the live token of each of
     * the user's machines, so no machine token outlives an agent that is disabled, deleted or loses its
     * Kaya grant. It writes no audit entry; the caller writes one for the whole action.
     *
     * @param organizationId the organization whose machines are retired
     * @param userId         the user whose machines are retired
     * @return the number of machines retired
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         user is not a member of the organization
     */
    int retireAllOfUser(UUID organizationId, UUID userId);

    /**
     * Locks the machine of a machine token until the caller's transaction ends and checks that it is
     * still ACTIVE. The token was checked when the request was authenticated, but a retirement of the
     * machine may have committed since, or may be running: the lock waits for a running one, and the
     * check then refuses the request, so the caller's transaction rolls back and nothing it wrote lands
     * after the machine was retired.
     *
     * @param principal the authenticated machine token
     * @return the locked machine, with its user
     * @throws com.korur.oba.dojo.exception.internal.auth.UnauthorizedException KAY_SEC_003 when the
     *         machine is retired or not in the token's organization
     */
    Machine lockActiveMachine(MachinePrincipal principal);

    /**
     * Records that the recorder of a machine sent a batch: the Claude Code version and the
     * account it reported, and lastSeenAt. The row is written only when a reported value changed
     * or lastSeenAt is a minute old, so steady batches do not rewrite it every time. A value the
     * batch does not report is kept.
     *
     * @param machine           the machine of the batch, locked by {@link #lockActiveMachine}, so the
     *                          write cannot set a machine retired meanwhile back to ACTIVE
     * @param claudeCodeVersion the version the batch reported, or null
     * @param account           the account of the batch, or null
     * @return the machine, with its user and account
     */
    Machine recordSeen(Machine machine, String claudeCodeVersion, Account account);

    /**
     * Stores the usage limits a batch carried as the machine's snapshot, unless the stored one was
     * captured later. capturedAt is clamped to the receive time. A snapshot without any window
     * stores nothing.
     *
     * @param machine     the machine of the batch
     * @param account   the account of the batch, or null
     * @param sessionId the session whose status line captured the values
     * @param request   the snapshot
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException KAY_VAL_009 when a
     *         percentage is outside 0 to 100 or a reset time is more than 8 days from capturedAt
     */
    void recordUsage(Machine machine, Account account, String sessionId, MachineUsageSnapshotRequest request);
}
