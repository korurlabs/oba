package com.korur.oba.shrine.agent.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

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
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineQueryService;
import com.korur.oba.shrine.agent.service.AgentQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Reads agents through the identity query service, their live tokens through the token store, their
 * routines from the routine store and their apps from the organization's USER grants. Every lookup is
 * scoped to the organization, so an id of a person or of another organization's agent answers the same
 * 404 as an unknown id.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentQueryServiceImpl implements AgentQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentQueryServiceImpl.class);

    private final UserQueryService userQueryService;
    private final ApiTokenQueryService apiTokenQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final AgentRoutineQueryService agentRoutineQueryService;
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
        Optional<User> agent = userQueryService.findAgent(organizationId, agentId);
        if (agent.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_NOT_FOUND,
                    organizationId, agentId);
            throw new NotFoundException(ExceptionDomainAgentError.NOT_AGENT_FOUND,
                    Map.of(AgentConstant.FIELD_AGENT_ID, agentId.toString()));
        }
        return agent.get();
    }

    @Override
    public AgentOptionsResponse getOptions() {
        List<AgentFlowOptionResponse> flows = new ArrayList<>();
        for (AgentFlow flow : AgentFlow.values()) {
            flows.add(agentMapper.toFlowOptionResponse(flow, agentFlowHelper.providersOf(flow)));
        }
        return new AgentOptionsResponse(List.copyOf(flows));
    }

    private List<AgentResponse> toResponses(User actor, UUID organizationId, List<User> agents) {
        if (agents.isEmpty()) {
            return List.of();
        }
        Set<UUID> agentIds = new HashSet<>();
        for (User agent : agents) {
            agentIds.add(agent.getId());
        }
        Map<UUID, ApiToken> tokens = new HashMap<>();
        for (ApiToken token : apiTokenQueryService.findLive(ApiTokenKind.AGENT, agentIds)) {
            tokens.put(token.getSubjectId(), token);
        }
        Map<UUID, AgentRoutine> routines = new HashMap<>();
        for (AgentRoutine routine : agentRoutineQueryService.findAll(organizationId, agentIds)) {
            routines.put(routine.getUserId(), routine);
        }
        Map<UUID, List<App>> apps = appsByAgent(actor, organizationId, agentIds);
        List<AgentResponse> responses = new ArrayList<>();
        for (User agent : agents) {
            List<App> agentApps = apps.get(agent.getId());
            if (agentApps == null) {
                agentApps = List.of();
            }
            responses.add(agentMapper.toResponse(agent, agentApps, tokens.get(agent.getId()),
                    routines.get(agent.getId())));
        }
        return responses;
    }

    /**
     * The apps of each agent are its USER grants; an agent belongs to no group but Everyone, whose
     * grants do not apply to agents.
     */
    private Map<UUID, List<App>> appsByAgent(User actor, UUID organizationId, Collection<UUID> agentIds) {
        // An EnumSet keeps each agent's apps in enum order without a separate sort.
        Map<UUID, Set<App>> appSets = new HashMap<>();
        for (AppGrantResponse grant : appAccessQueryService.listGrants(actor, organizationId)) {
            if (grant.targetType() != AppGrantTargetType.USER || !agentIds.contains(grant.userId())) {
                continue;
            }
            Set<App> agentApps = appSets.get(grant.userId());
            if (agentApps == null) {
                agentApps = EnumSet.noneOf(App.class);
                appSets.put(grant.userId(), agentApps);
            }
            agentApps.add(grant.app());
        }
        Map<UUID, List<App>> apps = new HashMap<>();
        for (Map.Entry<UUID, Set<App>> entry : appSets.entrySet()) {
            apps.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return apps;
    }
}
