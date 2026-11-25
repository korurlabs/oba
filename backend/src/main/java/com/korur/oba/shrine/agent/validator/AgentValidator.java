package com.korur.oba.shrine.agent.validator;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.constant.validator.AgentValidatorLogConstant;
import com.korur.oba.shrine.agent.exception.ExceptionDomainAgentError;
import com.korur.oba.shrine.agent.helper.AgentHelper;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Refuses what an agent may not do and what may not be done to an agent.
 *
 * An agent acts with a token from a hosted session whose input other people write, so the actions
 * that close, delete or outlive its work, or that mint a credential, are refused to it as the caller
 * with 403 AGT_SEC_002. People management and the higher roles do not apply to an agent as the
 * target, because its membership and status follow the Agents page and its grants stay narrow; those
 * answer 422 AGT_VAL_001. The services that own each action call this validator, so the rule holds
 * for every client.
 *
 * It also keeps agent names unique in an organization, answering 409 AGT_STA_001.
 */
@Component
@RequiredArgsConstructor
public class AgentValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentValidator.class);

    private final UserQueryService userQueryService;
    private final LoggingService loggingService;

    /**
     * Tells whether a user is an agent.
     *
     * @param user the user, may be null
     * @return true for a user of kind AGENT
     */
    public static boolean isAgent(User user) {
        return user != null && user.getKind() == UserKind.AGENT;
    }

    /**
     * Refuses an action to an agent caller.
     *
     * @param caller the user who calls
     * @param action the refused action, one of the action names in {@link AgentConstant}
     * @throws ForbiddenException AGT_SEC_002 when the caller is an agent
     */
    public void ensureCallerIsNotAgent(User caller, String action) {
        if (!isAgent(caller)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AgentValidatorLogConstant.AGENT_CALLER_REFUSED,
                caller.getId(), action);
        throw new ForbiddenException(ExceptionDomainAgentError.SEC_AGENT_ACTION_REFUSED,
                Map.of(AgentConstant.FIELD_ACTION, action));
    }

    /**
     * Refuses an action on an agent target.
     *
     * @param target the user the action changes, may be null
     * @param action the refused action, one of the action names in {@link AgentConstant}
     * @throws ValidationException AGT_VAL_001 when the target is an agent
     */
    public void ensureTargetIsNotAgent(User target, String action) {
        if (!isAgent(target)) {
            return;
        }
        throw targetRefused(target.getId(), action);
    }

    /**
     * Refuses an action that names agents among its target users. The users are read in one query; an
     * unknown id is left to the caller's own existence check.
     *
     * @param userIds the users the action names; empty passes without a query
     * @param action  the refused action, one of the action names in {@link AgentConstant}
     * @throws ValidationException AGT_VAL_001 naming the first agent found
     */
    public void ensureNoAgentAmong(Collection<UUID> userIds, String action) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<UUID> distinctIds = new LinkedHashSet<>(userIds);
        List<UUID> ids = new ArrayList<>(distinctIds);
        for (User user : userQueryService.getUserEntitiesByIds(ids)) {
            if (isAgent(user)) {
                throw targetRefused(user.getId(), action);
            }
        }
    }

    /**
     * Refuses a name another agent of the organization already has, ignoring case.
     *
     * @param organizationId the organization of the agent
     * @param name           the stripped name
     * @param agentId        the agent being renamed, or null for a new agent
     * @throws ConflictException AGT_STA_001 naming the name when it is taken
     */
    public void ensureNameFree(UUID organizationId, String name, UUID agentId) {
        if (!userQueryService.existsAgentName(organizationId, name, agentId)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AgentValidatorLogConstant.AGENT_NAME_TAKEN,
                organizationId, agentId);
        throw new ConflictException(ExceptionDomainAgentError.STA_NAME_TAKEN, Map.of(AgentConstant.FIELD_NAME, name));
    }

    /**
     * Refuses a write that failed on the agent name index. A parallel request can take the name after
     * {@link #ensureNameFree} passed, so the index gives the same 409; the caller passes any other
     * integrity failure on.
     *
     * @param exception      the failure of the write
     * @param organizationId the organization of the agent
     * @param agentId        the agent being renamed, or null for a new agent
     * @throws ConflictException AGT_STA_001 when the violated constraint is the agent name index
     */
    public void ensureNoNameConflict(DataIntegrityViolationException exception, UUID organizationId, UUID agentId) {
        if (!AgentHelper.isNameConflict(exception)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AgentValidatorLogConstant.AGENT_NAME_TAKEN,
                organizationId, agentId);
        throw new ConflictException(ExceptionDomainAgentError.STA_NAME_TAKEN);
    }

    private ValidationException targetRefused(UUID agentId, String action) {
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, AgentValidatorLogConstant.AGENT_TARGET_REFUSED,
                agentId, action);
        return new ValidationException(ExceptionDomainAgentError.VAL_AGENT_TARGET_REFUSED,
                Map.of(AgentConstant.FIELD_ACTION, action, AgentConstant.FIELD_USER_ID, agentId.toString()));
    }
}
