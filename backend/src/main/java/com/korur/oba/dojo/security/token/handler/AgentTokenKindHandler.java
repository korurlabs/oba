package com.korur.oba.dojo.security.token.handler;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.token.constant.AgentTokenKindHandlerLogConstant;
import com.korur.oba.dojo.security.token.dto.ApiTokenBodyLimit;
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.dojo.security.token.dto.UserApiTokenPrincipal;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.shrine.agent.exception.ExceptionDomainAgentError;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * The agent token: the bearer credential of a ROUTINE agent's hosted session, acting as the agent user in
 * the agent's organization.
 *
 * It reserves no path: the agent may call what its MEMBER role and app grants allow, which the request
 * access check applies to every annotated handler. The only unannotated handler it may call is reading
 * its own user ({@link SecurityConstant#AGENT_TOKEN_EXEMPT_ENDPOINTS}), which skips that check, so the
 * principal is built only after this handler has found the user to be an ACTIVE agent of the token's
 * organization, with an ACTIVE membership in an ACTIVE organization. A disabled or deleted agent's token
 * is therefore refused at once, with the one 401 of the kind.
 */
@Component
@RequiredArgsConstructor
public class AgentTokenKindHandler implements ApiTokenKindHandler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentTokenKindHandler.class);

    private final UserQueryService userQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    @Override
    public ApiTokenKind kind() {
        return ApiTokenKind.AGENT;
    }

    @Override
    public List<String> reservedPaths() {
        return List.of();
    }

    @Override
    public List<ApiTokenEndpoint> exemptEndpoints() {
        return SecurityConstant.AGENT_TOKEN_EXEMPT_ENDPOINTS;
    }

    @Override
    public Optional<ApiTokenBodyLimit> bodyLimit() {
        return Optional.empty();
    }

    /**
     * @throws UnauthorizedException AGT_SEC_001 when the token's user is gone, is not an agent of the
     *                               token's organization, or the agent, its membership or the organization
     *                               is not ACTIVE
     */
    @Override
    public ApiTokenPrincipal resolvePrincipal(ApiToken token) {
        UUID organizationId = token.getOrganizationId();
        UUID agentId = token.getSubjectId();

        Optional<User> found = token.getUserId().equals(agentId)
                ? userQueryService.getUserEntitiesByIds(List.of(agentId)).stream().findFirst()
                : Optional.empty();
        if (found.isEmpty()) {
            throw refuse(token, AgentTokenKindHandlerLogConstant.REASON_USER_MISSING);
        }
        User agent = found.get();
        if (agent.getKind() != UserKind.AGENT || agent.getAgentDetail() == null
                || !organizationId.equals(agent.getAgentDetail().getOrganizationId())) {
            throw refuse(token, AgentTokenKindHandlerLogConstant.REASON_NOT_AGENT);
        }
        if (agent.getStatus() != UserStatus.ACTIVE) {
            throw refuse(token, AgentTokenKindHandlerLogConstant.REASON_USER_NOT_ACTIVE);
        }
        Optional<OrganizationMember> member = organizationMemberQueryService.findMember(organizationId, agentId);
        if (member.isEmpty() || member.get().getStatus() != OrganizationMemberStatus.ACTIVE) {
            throw refuse(token, AgentTokenKindHandlerLogConstant.REASON_MEMBERSHIP_NOT_ACTIVE);
        }
        if (member.get().getOrganization().getStatus() != OrganizationStatus.ACTIVE) {
            throw refuse(token, AgentTokenKindHandlerLogConstant.REASON_ORGANIZATION_NOT_ACTIVE);
        }
        return new UserApiTokenPrincipal(token.getId(), agentId, organizationId);
    }

    @Override
    public ExceptionDomainError invalidTokenError() {
        return ExceptionDomainAgentError.SEC_TOKEN_INVALID;
    }

    private UnauthorizedException refuse(ApiToken token, String reason) {
        loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, AgentTokenKindHandlerLogConstant.AGENT_TOKEN_REFUSED,
                token.getId(), token.getSubjectId(), token.getOrganizationId(), reason);
        return new UnauthorizedException(ExceptionDomainAgentError.SEC_TOKEN_INVALID);
    }
}
