package com.korur.oba.shrine.agent.routine.service;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.routine.dto.request.AgentRoutineSetRequest;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Sets, removes and fires the routine of a ROUTINE agent. The routine secret is stored encrypted and
 * leaves this service only towards the vendor; no response, log or audit entry carries it.
 */
public interface AgentRoutineService {

    /**
     * Sets the agent's routine, replacing the one it had. A replaced routine loses its last fire
     * result, which described the old routine.
     *
     * @param actor          the calling ADMIN
     * @param organizationId the organization
     * @param agentId        the agent's user id
     * @param request        the routine id and secret
     * @return the agent with its routine, showing only the secret prefix
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException AGT_NOT_001 for an unknown
     *         id, a person or another organization's agent
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException AGT_VAL_003 for an API
     *         agent, AGT_VAL_002 when the id does not have the provider's format
     */
    AgentResponse set(User actor, UUID organizationId, UUID agentId, AgentRoutineSetRequest request);

    /**
     * Removes the agent's routine, so an assignment fires nothing; an agent without one is left
     * unchanged.
     *
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException AGT_NOT_001 for an unknown
     *         id, a person or another organization's agent
     */
    void remove(User actor, UUID organizationId, UUID agentId);

    /**
     * Fires the routine of an agent with the ticket key as its only input, and records the result on
     * the routine. Runs on the caller's thread: the caller puts it off the request thread. A vendor
     * failure is recorded and returned, not thrown.
     *
     * @param organizationId the organization of the assignment
     * @param userId         the assigned user
     * @param ticketKey      the key of the assigned ticket
     * @return the fire result, or empty unless the user is an ACTIVE ROUTINE agent of the organization
     *         with a routine
     */
    Optional<RoutineFireResult> fire(UUID organizationId, UUID userId, String ticketKey);
}
