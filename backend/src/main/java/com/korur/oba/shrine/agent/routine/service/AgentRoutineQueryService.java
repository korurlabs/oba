package com.korur.oba.shrine.agent.routine.service;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;

/**
 * Reads the routines of agents. Every lookup is scoped to the organization.
 */
public interface AgentRoutineQueryService {

    /**
     * @param organizationId the organization
     * @param agentId        the agent's user id
     * @return the agent's routine, or empty when it has none
     */
    Optional<AgentRoutine> find(UUID organizationId, UUID agentId);

    /**
     * @param organizationId the organization
     * @param agentIds       the agents' user ids
     * @return the routines of those agents that have one
     */
    List<AgentRoutine> findAll(UUID organizationId, Collection<UUID> agentIds);
}
