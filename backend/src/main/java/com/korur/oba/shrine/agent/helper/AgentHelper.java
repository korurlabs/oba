package com.korur.oba.shrine.agent.helper;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Reads and describes what belongs to an agent for the agent services: its USER grants, the values an
 * audit entry records and whether a write failed on the agent name index.
 *
 * Audit values carry ids, enum values and the token prefix, never the agent name, a token or a secret.
 */
@Component
@RequiredArgsConstructor
public class AgentHelper {

    private final AppAccessQueryService appAccessQueryService;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;

    /**
     * Tells whether a failed write hit the unique index of agent names in an organization. A parallel
     * request can take a name after the service checked it, so the index is the last guard; any other
     * integrity failure is not a name conflict.
     *
     * @param exception the failure of the write
     * @return true when the violated constraint is the agent name index
     */
    public static boolean isNameConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return AgentConstant.AGENT_NAME_UNIQUE_INDEX.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Reads the agent's own grants. Grants to everyone or to a group are left out, because the agent's
     * apps are the ones granted to it alone.
     *
     * @param actor          the user who calls
     * @param organizationId the organization of the agent
     * @param agentId        the agent user
     * @return the agent's USER grants by grant id
     */
    public Map<UUID, App> userGrants(User actor, UUID organizationId, UUID agentId) {
        Map<UUID, App> grants = new HashMap<>();
        for (AppGrantResponse grant : appAccessQueryService.listGrants(actor, organizationId)) {
            if (grant.targetType() == AppGrantTargetType.USER && agentId.equals(grant.userId())) {
                grants.put(grant.id(), grant.app());
            }
        }
        return grants;
    }

    /**
     * Describes the role and apps of an agent for an audit entry.
     *
     * @param agent the agent user
     * @param apps  the agent's apps
     * @return the role and the app names in name order, in a map the caller may extend
     */
    public Map<String, Object> describe(User agent, Collection<App> apps) {
        Map<String, Object> values = new HashMap<>();
        values.put(AgentConstant.FIELD_ROLE, agent.getAgentDetail().getRole().name());
        List<String> appNames = new ArrayList<>();
        for (App app : apps) {
            appNames.add(app.name());
        }
        Collections.sort(appNames);
        values.put(AgentConstant.FIELD_APPS, List.copyOf(appNames));
        return values;
    }

    /**
     * Describes an agent token for an audit entry, without its secret part.
     *
     * @param token the token
     * @return the token id, prefix and expiry, in a map the caller may extend
     */
    public Map<String, Object> tokenDetails(ApiToken token) {
        Map<String, Object> details = new HashMap<>();
        details.put(AgentConstant.FIELD_TOKEN_ID, token.getId().toString());
        details.put(AgentConstant.FIELD_TOKEN_PREFIX, token.getTokenPrefix());
        details.put(AgentConstant.FIELD_EXPIRES_AT, token.getExpiresAt().toString());
        return details;
    }

    /**
     * Writes an audit entry of a change to an agent.
     *
     * @param organizationId the organization of the agent
     * @param actor          the user who made the change
     * @param action         the audit action, one of the actions in {@link AgentConstant}
     * @param agentId        the agent user
     * @param oldValues      the values before the change, or null
     * @param newValues      the values after the change, or null
     */
    public void audit(UUID organizationId, User actor, String action, UUID agentId,
            Map<String, Object> oldValues, Map<String, Object> newValues) {
        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(), action,
                AgentConstant.AUDIT_ENTITY_TYPE, agentId, oldValues, newValues));
    }
}
