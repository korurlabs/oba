package com.korur.oba.shrine.agent.mapper;

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.agent.dto.response.AgentFlowOptionResponse;
import com.korur.oba.shrine.agent.dto.response.AgentTokenCreatedResponse;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;

/**
 * Maps agent users, their app grants and their live token to the agent responses.
 */
@Mapper(componentModel = "spring")
public interface AgentMapper {

    /**
     * Maps an agent without its routine; the routine fields stay null until a routine is read with it.
     *
     * @param agent the agent user
     * @param apps  the apps granted to the agent
     * @param token the agent's live token, or null
     * @return the agent response
     */
    @Mapping(target = "id", source = "agent.id")
    @Mapping(target = "name", source = "agent.profile.firstName")
    @Mapping(target = "flow", source = "agent.agentDetail.flow")
    @Mapping(target = "provider", source = "agent.agentDetail.provider")
    @Mapping(target = "role", source = "agent.agentDetail.role")
    @Mapping(target = "apps", source = "apps")
    @Mapping(target = "status", source = "agent.status")
    @Mapping(target = "tokenPrefix", source = "token.tokenPrefix")
    @Mapping(target = "tokenLastUsedAt", source = "token.lastUsedAt")
    @Mapping(target = "tokenExpiresAt", source = "token.expiresAt")
    @Mapping(target = "routineId", ignore = true)
    @Mapping(target = "routineSecretPrefix", ignore = true)
    @Mapping(target = "lastFiredAt", ignore = true)
    @Mapping(target = "lastFireStatus", ignore = true)
    @Mapping(target = "lastSessionUrl", ignore = true)
    AgentResponse toResponse(User agent, List<App> apps, ApiToken token);

    @Mapping(target = "agentId", source = "agentId")
    @Mapping(target = "token", source = "plaintext")
    @Mapping(target = "tokenPrefix", source = "token.tokenPrefix")
    @Mapping(target = "expiresAt", source = "token.expiresAt")
    AgentTokenCreatedResponse toTokenCreatedResponse(UUID agentId, ApiToken token, String plaintext);

    AgentFlowOptionResponse toFlowOptionResponse(AgentFlow flow, List<AgentProvider> providers);
}
