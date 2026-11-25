package com.korur.oba.shrine.agent.service;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.agent.dto.request.AgentCreateRequest;
import com.korur.oba.shrine.agent.dto.request.AgentStatusUpdateRequest;
import com.korur.oba.shrine.agent.dto.request.AgentUpdateRequest;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.dto.response.AgentTokenCreatedResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Registers and changes the agents of an organization and their API tokens. Every change is audited
 * with ids, never with a token.
 */
public interface AgentService {

    /**
     * Registers an agent: an ACTIVE user of kind AGENT, an ACTIVE MEMBER of the organization, and one
     * USER grant per app.
     *
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException AGT_VAL_003 when the
     *         flow does not accept the provider
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException AGT_STA_001 when another
     *         live agent of the organization has the name
     */
    AgentResponse createAgent(User actor, UUID organizationId, AgentCreateRequest request);

    /**
     * Changes the name, role and apps of an agent; the apps replace its USER grants.
     *
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException AGT_STA_001 when another
     *         live agent of the organization has the name
     */
    AgentResponse updateAgent(User actor, UUID organizationId, UUID agentId, AgentUpdateRequest request);

    /**
     * Enables or disables an agent: its user and its membership take the status. A disabled agent's
     * token is refused, and works again once the agent is enabled.
     */
    AgentResponse updateStatus(User actor, UUID organizationId, UUID agentId, AgentStatusUpdateRequest request);

    /**
     * Deletes an agent: revokes its token, removes its grants and group rows, and soft-deletes its user
     * and membership.
     */
    void deleteAgent(User actor, UUID organizationId, UUID agentId);

    /**
     * Issues a token to a ROUTINE agent and revokes the one it had. The plaintext is returned this once.
     *
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException AGT_VAL_003 for an API
     *         agent, which Oba runs itself and which so has no use for a token
     */
    AgentTokenCreatedResponse issueToken(User actor, UUID organizationId, UUID agentId);

    /**
     * Revokes the agent's live token; an agent without one is left unchanged.
     */
    void revokeToken(User actor, UUID organizationId, UUID agentId);
}
