package com.korur.oba.shrine.agent.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.token.dto.IssuedApiToken;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.service.ApiTokenService;
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.constant.service.AgentServiceLogConstant;
import com.korur.oba.shrine.agent.dto.request.AgentCreateRequest;
import com.korur.oba.shrine.agent.dto.request.AgentStatusUpdateRequest;
import com.korur.oba.shrine.agent.dto.request.AgentUpdateRequest;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.dto.response.AgentTokenCreatedResponse;
import com.korur.oba.shrine.agent.exception.ExceptionDomainAgentError;
import com.korur.oba.shrine.agent.helper.AgentFlowHelper;
import com.korur.oba.shrine.agent.helper.AgentHelper;
import com.korur.oba.shrine.agent.mapper.AgentMapper;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineService;
import com.korur.oba.shrine.agent.service.AgentQueryService;
import com.korur.oba.shrine.agent.service.AgentService;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.service.UserService;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberService;

/**
 * Registers and changes agents. An agent is a user of kind AGENT with one MEMBER membership and a USER
 * grant per app, so the existing role and app checks apply to it unchanged; this service only keeps the
 * user, the membership, the grants and the token in step.
 *
 * Which flow accepts which provider comes from {@link AgentFlowHelper}, and a token exists only for a
 * ROUTINE agent: an API agent is run by Oba itself, so a token would be an unused credential. Audit
 * entries carry ids and enum values, never a token.
 *
 * Disabling and deleting an agent end its access the way they end a person's: {@link AccessRevokedEvent}
 * is published for the membership, so open sessions and other state built on that access are torn down
 * once the change commits.
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentServiceImpl.class);

    private final UserService userService;
    private final AgentQueryService agentQueryService;
    private final OrganizationMemberService organizationMemberService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessService appAccessService;
    private final ApiTokenService apiTokenService;
    private final AgentRoutineService agentRoutineService;
    private final AgentFlowHelper agentFlowHelper;
    private final AgentHelper agentHelper;
    private final AgentValidator agentValidator;
    private final AgentMapper agentMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public AgentResponse createAgent(User actor, UUID organizationId, AgentCreateRequest request) {
        if (!agentFlowHelper.accepts(request.flow(), request.provider())) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_FLOW_PROVIDER_REFUSED,
                    organizationId, request.flow(), request.provider());
            throw new ValidationException(ExceptionDomainAgentError.VAL_FLOW_PROVIDER_UNSUPPORTED,
                    Map.of(AgentConstant.FIELD_FLOW, request.flow().name(),
                            AgentConstant.FIELD_PROVIDER, request.provider().name()));
        }
        String name = request.name().strip();
        agentValidator.ensureNameFree(organizationId, name, null);

        UserAgentDetail agentDetail = UserAgentDetail.builder()
                .role(request.role())
                .flow(request.flow())
                .provider(request.provider())
                .organizationId(organizationId)
                .createdByUserId(actor.getId())
                .build();
        String email = AgentConstant.AGENT_EMAIL_PREFIX + UUID.randomUUID() + AgentConstant.AGENT_EMAIL_DOMAIN;
        User agent = saveName(organizationId, null, () -> userService.createAgentUser(email, name, agentDetail));

        organizationMemberService.saveMember(OrganizationMember.builder()
                .organization(agent.getDefaultOrganization())
                .user(agent)
                .role(OrganizationMemberRole.MEMBER)
                .status(OrganizationMemberStatus.ACTIVE)
                .build());
        Set<App> apps = EnumSet.copyOf(request.apps());
        for (App app : apps) {
            appAccessService.grant(actor, organizationId, new AppGrantCreateRequest(app, agent.getId(), null));
        }

        Map<String, Object> newValues = agentHelper.describe(agent, apps);
        newValues.put(AgentConstant.FIELD_FLOW, request.flow().name());
        newValues.put(AgentConstant.FIELD_PROVIDER, request.provider().name());
        agentHelper.audit(organizationId, actor, AgentConstant.AUDIT_ACTION_AGENT_CREATED, agent.getId(), null,
                newValues);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_CREATED,
                organizationId, agent.getId(), request.flow(), request.provider(), apps);
        return agentQueryService.getAgent(actor, organizationId, agent.getId());
    }

    @Override
    @Transactional
    public AgentResponse updateAgent(User actor, UUID organizationId, UUID agentId, AgentUpdateRequest request) {
        User agent = agentQueryService.getAgentEntity(organizationId, agentId);
        String name = request.name().strip();
        agentValidator.ensureNameFree(organizationId, name, agentId);

        Map<UUID, App> currentGrants = agentHelper.userGrants(actor, organizationId, agentId);
        Map<String, Object> oldValues = agentHelper.describe(agent, currentGrants.values());

        saveName(organizationId, agentId, () -> userService.updateAgentUser(agent, name, request.role(), null));

        Set<App> apps = EnumSet.copyOf(request.apps());
        for (Map.Entry<UUID, App> grant : currentGrants.entrySet()) {
            if (!apps.contains(grant.getValue())) {
                appAccessService.revoke(actor, organizationId, grant.getKey());
            }
        }
        for (App app : apps) {
            if (!currentGrants.containsValue(app)) {
                appAccessService.grant(actor, organizationId, new AppGrantCreateRequest(app, agentId, null));
            }
        }

        agentHelper.audit(organizationId, actor, AgentConstant.AUDIT_ACTION_AGENT_UPDATED, agentId, oldValues,
                agentHelper.describe(agent, apps));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_UPDATED,
                organizationId, agentId, apps);
        return agentQueryService.getAgent(actor, organizationId, agentId);
    }

    @Override
    @Transactional
    public AgentResponse updateStatus(User actor, UUID organizationId, UUID agentId, AgentStatusUpdateRequest request) {
        User agent = agentQueryService.getAgentEntity(organizationId, agentId);
        Optional<OrganizationMember> foundMember = organizationMemberQueryService.findMember(organizationId, agentId);
        if (foundMember.isEmpty()) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_MEMBERSHIP_MISSING,
                    organizationId, agentId);
            throw new NotFoundException(ExceptionDomainAgentError.NOT_AGENT_FOUND,
                    Map.of(AgentConstant.FIELD_AGENT_ID, agentId.toString()));
        }
        OrganizationMember member = foundMember.get();

        OrganizationMemberStatus status = request.status();
        UserStatus userStatus = UserStatus.valueOf(status.name());
        if (member.getStatus() == status && agent.getStatus() == userStatus) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_STATUS_UNCHANGED,
                    organizationId, agentId, status);
            return agentQueryService.getAgent(actor, organizationId, agentId);
        }

        Map<String, Object> oldValues = Map.of(AgentConstant.FIELD_STATUS, agent.getStatus().name());
        userService.updateAgentUser(agent, null, null, userStatus);
        member.setStatus(status);
        organizationMemberService.saveMember(member);
        if (status == OrganizationMemberStatus.DISABLED) {
            applicationEventPublisher.publishEvent(AccessRevokedEvent.member(agentId, organizationId));
        }

        agentHelper.audit(organizationId, actor, AgentConstant.AUDIT_ACTION_AGENT_STATUS_CHANGED, agentId, oldValues,
                Map.of(AgentConstant.FIELD_STATUS, userStatus.name()));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_STATUS_CHANGED,
                organizationId, agentId, status);
        return agentQueryService.getAgent(actor, organizationId, agentId);
    }

    @Override
    @Transactional
    public void deleteAgent(User actor, UUID organizationId, UUID agentId) {
        User agent = agentQueryService.getAgentEntity(organizationId, agentId);

        Optional<ApiToken> revoked = apiTokenService.revoke(ApiTokenKind.AGENT, agentId);
        // The database removes the routine only with a hard delete of the user; a soft-deleted agent must
        // not keep a secret that can still be fired.
        agentRoutineService.remove(actor, organizationId, agentId);
        appAccessService.removeMemberAccess(actor, organizationId, agentId);
        userService.deleteAgentUser(agent);
        applicationEventPublisher.publishEvent(AccessRevokedEvent.member(agentId, organizationId));

        UUID revokedTokenId = null;
        if (revoked.isPresent()) {
            revokedTokenId = revoked.get().getId();
        }
        Map<String, Object> newValues = new HashMap<>();
        newValues.put(AgentConstant.FIELD_SOFT_DELETED, true);
        if (revokedTokenId != null) {
            newValues.put(AgentConstant.FIELD_REVOKED_TOKEN_ID, revokedTokenId.toString());
        }
        agentHelper.audit(organizationId, actor, AgentConstant.AUDIT_ACTION_AGENT_DELETED, agentId,
                Map.of(AgentConstant.FIELD_SOFT_DELETED, false), newValues);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_DELETED,
                organizationId, agentId, revokedTokenId);
    }

    @Override
    @Transactional
    public AgentTokenCreatedResponse issueToken(User actor, UUID organizationId, UUID agentId) {
        User agent = agentQueryService.getAgentEntity(organizationId, agentId);
        AgentFlow flow = agent.getAgentDetail().getFlow();
        if (flow != AgentFlow.ROUTINE) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_TOKEN_FLOW_REFUSED,
                    organizationId, agentId, flow);
            throw new ValidationException(ExceptionDomainAgentError.VAL_FLOW_PROVIDER_UNSUPPORTED,
                    Map.of(AgentConstant.FIELD_AGENT_ID, agentId.toString(), AgentConstant.FIELD_FLOW, flow.name()));
        }

        // Owned by the agent user and valid only in the agent's organization; the live token is revoked
        // in the same transaction.
        IssuedApiToken issued = apiTokenService.issue(ApiTokenKind.AGENT, agentId, agent,
                agent.getAgentDetail().getOrganizationId());
        ApiToken token = issued.token();

        Map<String, Object> newValues = agentHelper.tokenDetails(token);
        if (issued.revokedTokenId() != null) {
            newValues.put(AgentConstant.FIELD_REVOKED_TOKEN_ID, issued.revokedTokenId().toString());
        }
        agentHelper.audit(organizationId, actor, AgentConstant.AUDIT_ACTION_AGENT_TOKEN_ISSUED, agentId, null,
                newValues);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_TOKEN_ISSUED,
                organizationId, agentId, token.getId(), issued.revokedTokenId());
        return agentMapper.toTokenCreatedResponse(agentId, token, issued.plaintext());
    }

    @Override
    @Transactional
    public void revokeToken(User actor, UUID organizationId, UUID agentId) {
        agentQueryService.getAgentEntity(organizationId, agentId);

        Optional<ApiToken> revoked = apiTokenService.revoke(ApiTokenKind.AGENT, agentId);
        if (revoked.isEmpty()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_NO_TOKEN_TO_REVOKE,
                    organizationId, agentId);
            return;
        }
        agentHelper.audit(organizationId, actor, AgentConstant.AUDIT_ACTION_AGENT_TOKEN_REVOKED, agentId,
                agentHelper.tokenDetails(revoked.get()), null);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentServiceLogConstant.AGENT_TOKEN_REVOKED,
                organizationId, agentId, revoked.get().getId());
    }

    /**
     * Runs a write that sets the agent's name and turns a failure on the agent name index into the same
     * 409 the name check gives.
     */
    private User saveName(UUID organizationId, UUID agentId, Supplier<User> write) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException exception) {
            agentValidator.ensureNoNameConflict(exception, organizationId, agentId);
            throw exception;
        }
    }
}
