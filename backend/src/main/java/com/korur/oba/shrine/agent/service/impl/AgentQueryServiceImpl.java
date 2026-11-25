package com.korur.oba.shrine.agent.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.constant.service.AgentServiceLogConstant;
import com.korur.oba.shrine.agent.dto.response.AgentFlowOptionResponse;
import com.korur.oba.shrine.agent.dto.response.AgentOptionsResponse;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.exception.ExceptionDomainAgentError;
import com.korur.oba.shrine.agent.helper.AgentFlowHelper;
import com.korur.oba.shrine.agent.mapper.AgentMapper;
import com.korur.oba.shrine.agent.service.AgentQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Reads agents through the identity query service, their live tokens through the token store and their
 * apps from the organization's USER grants. Every lookup is scoped to the organization, so an id of a
 * person or of another organization's agent answers the same 404 as an unknown id.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentQueryServiceImpl implements AgentQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentQueryServiceImpl.class);

    private final UserQueryService userQueryService;
    private final ApiTokenQueryService apiTokenQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final AgentFlowHelper agentFlowHelper;
    private final AgentMapper agentMapper;
    private final LoggingService loggingService;

    @Override
    public List<AgentResponse> listAgents(User actor, UUID organizationId) {
        List<User> agents = userQueryService.getAgentsOfOrganization(organizationId);
        return toResponses(actor, organizationId, agents);
    }

    @Override
    public AgentResponse getAgent(User actor, UUID organizationId, UUID agentId) {
        User agent = getAgentEntity(organizationId, agentId);
        return toResponses(actor, organizationId, List.of(agent)).getFirst();
    }

    @Override
    public User getAgentEntity(UUID organizationId, UUID agentId) {
        return userQueryService.findAgent(organizationId, agentId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_NOT_FOUND,
                    organizationId, agentId);
            return new NotFoundException(ExceptionDomainAgentError.NOT_AGENT_FOUND,
                    Map.of(AgentConstant.FIELD_AGENT_ID, agentId.toString()));
        });
    }

    @Override
    public AgentOptionsResponse getOptions() {
        List<AgentFlowOptionResponse> flows = Arrays.stream(AgentFlow.values())
                .map(flow -> agentMapper.toFlowOptionResponse(flow, agentFlowHelper.providersOf(flow)))
                .toList();
        return new AgentOptionsResponse(flows);
    }

    private List<AgentResponse> toResponses(User actor, UUID organizationId, List<User> agents) {
        if (agents.isEmpty()) {
            return List.of();
        }
        Set<UUID> agentIds = agents.stream().map(User::getId).collect(Collectors.toSet());
        Map<UUID, ApiToken> tokens = apiTokenQueryService.findLive(ApiTokenKind.AGENT, agentIds).stream()
                .collect(Collectors.toMap(ApiToken::getSubjectId, Function.identity()));
        Map<UUID, List<App>> apps = appsByAgent(actor, organizationId, agentIds);
        return agents.stream()
                .map(agent -> agentMapper.toResponse(agent, apps.getOrDefault(agent.getId(), List.of()),
                        tokens.get(agent.getId())))
                .toList();
    }

    /**
     * The apps of each agent are its USER grants; an agent belongs to no group but Everyone, whose
     * grants do not apply to agents.
     */
    private Map<UUID, List<App>> appsByAgent(User actor, UUID organizationId, Collection<UUID> agentIds) {
        return appAccessQueryService.listGrants(actor, organizationId).stream()
                .filter(grant -> grant.targetType() == AppGrantTargetType.USER && agentIds.contains(grant.userId()))
                .collect(Collectors.groupingBy(AppGrantResponse::userId,
                        Collectors.mapping(AppGrantResponse::app, Collectors.collectingAndThen(
                                Collectors.toList(), list -> list.stream().sorted().toList()))));
    }
}
