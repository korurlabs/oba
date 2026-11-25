package com.korur.oba.shrine.agent.service;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.agent.dto.response.AgentOptionsResponse;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Reads the agents of an organization. An agent is a user of kind AGENT; there is no agent table.
 */
public interface AgentQueryService {

    /**
     * Lists the agents of an organization, disabled ones included, ordered by name.
     *
     * @param actor          the calling ADMIN
     * @param organizationId the organization
     * @return the agents with their apps and live token
     */
    List<AgentResponse> listAgents(User actor, UUID organizationId);

    /**
     * Reads one agent of an organization.
     *
     * @param actor          the calling ADMIN
     * @param organizationId the organization
     * @param agentId        the agent's user id
     * @return the agent with its apps and live token
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException AGT_NOT_001 for an unknown
     *         id, a person or another organization's agent
     */
    AgentResponse getAgent(User actor, UUID organizationId, UUID agentId);

    /**
     * Loads the user of an agent of an organization.
     *
     * @param organizationId the organization
     * @param agentId        the agent's user id
     * @return the agent user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException AGT_NOT_001 for an unknown
     *         id, a person or another organization's agent
     */
    User getAgentEntity(UUID organizationId, UUID agentId);

    /**
     * @return every flow with the providers it accepts in this application
     */
    AgentOptionsResponse getOptions();
}
