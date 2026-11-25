package com.korur.oba.shrine.agent.routine.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.connect.routine.core.exception.ExceptionDomainRoutineError;
import com.korur.oba.connect.routine.core.service.RoutineService;
import com.korur.oba.connect.routine.core.validator.RoutineValidator;
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.service.AESEncryptionService;
import com.korur.oba.shrine.agent.constant.AgentConstant;
import com.korur.oba.shrine.agent.dto.response.AgentResponse;
import com.korur.oba.shrine.agent.exception.ExceptionDomainAgentError;
import com.korur.oba.shrine.agent.helper.AgentFlowHelper;
import com.korur.oba.shrine.agent.routine.constant.AgentRoutineConstant;
import com.korur.oba.shrine.agent.routine.constant.service.AgentRoutineServiceLogConstant;
import com.korur.oba.shrine.agent.routine.dto.request.AgentRoutineSetRequest;
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;
import com.korur.oba.shrine.agent.routine.helper.AgentRoutineFireRecorder;
import com.korur.oba.shrine.agent.routine.repository.AgentRoutineRepository;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineQueryService;
import com.korur.oba.shrine.agent.routine.service.AgentRoutineService;
import com.korur.oba.shrine.agent.service.AgentQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserAgentDetail;
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Keeps the routine of a ROUTINE agent and fires it. The routine vendor is the agent's provider,
 * mapped by {@link AgentFlowHelper#routineProviderOf}; the id format belongs to the vendor's client and
 * is checked by {@link RoutineValidator} before the routine is saved.
 *
 * Only {@link #fire} decrypts the secret, and only to hand it to the vendor. Audit entries and logs
 * carry ids.
 */
@Service
@RequiredArgsConstructor
public class AgentRoutineServiceImpl implements AgentRoutineService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentRoutineServiceImpl.class);

    private final AgentRoutineRepository agentRoutineRepository;
    private final AgentRoutineQueryService agentRoutineQueryService;
    private final AgentQueryService agentQueryService;
    private final UserQueryService userQueryService;
    private final AgentFlowHelper agentFlowHelper;
    private final RoutineValidator routineValidator;
    private final RoutineService routineService;
    private final AESEncryptionService aesEncryptionService;
    private final AgentRoutineFireRecorder agentRoutineFireRecorder;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;
    private final Clock clock;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public AgentResponse set(User actor, UUID organizationId, UUID agentId, AgentRoutineSetRequest request) {
        User agent = agentQueryService.getAgentEntity(organizationId, agentId);
        RoutineProvider provider = routineProviderOrRefuse(organizationId, agent);
        String routineId = request.routineId().strip();
        ensureValidRoutineId(organizationId, agentId, agent, provider, routineId);
        String secret = request.routineSecret().strip();

        Optional<AgentRoutine> existing = agentRoutineQueryService.find(organizationId, agentId);
        AgentRoutine routine;
        Map<String, Object> oldValues = null;
        if (existing.isPresent()) {
            routine = existing.get();
            oldValues = describe(routine);
            routine.setRoutineId(routineId);
            // The last fire described the routine being replaced.
            routine.setLastFiredAt(null);
            routine.setLastFireStatus(null);
            routine.setLastHttpStatus(null);
            routine.setLastSessionUrl(null);
        } else {
            routine = AgentRoutine.builder()
                    .organizationId(organizationId)
                    .userId(agentId)
                    .routineId(routineId)
                    .build();
        }
        routine.setRoutineSecretEncrypted(aesEncryptionService.encrypt(secret));
        routine.setRoutineSecretPrefix(secretPrefix(secret));
        AgentRoutine saved = agentRoutineRepository.save(routine);

        audit(organizationId, actor, AgentRoutineConstant.AUDIT_ACTION_AGENT_ROUTINE_SET, agentId, oldValues,
                describe(saved));
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_SET,
                organizationId, agentId, saved.getId(), routineId, existing.isPresent());
        return agentQueryService.getAgent(actor, organizationId, agentId);
    }

    @Override
    @Transactional
    public void remove(User actor, UUID organizationId, UUID agentId) {
        agentQueryService.getAgentEntity(organizationId, agentId);

        Optional<AgentRoutine> existing = agentRoutineQueryService.find(organizationId, agentId);
        if (existing.isEmpty()) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_NO_ROUTINE_TO_REMOVE,
                    organizationId, agentId);
            return;
        }
        AgentRoutine routine = existing.get();
        agentRoutineRepository.delete(routine);

        audit(organizationId, actor, AgentRoutineConstant.AUDIT_ACTION_AGENT_ROUTINE_REMOVED, agentId,
                describe(routine), null);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_REMOVED,
                organizationId, agentId, routine.getId());
    }

    /**
     * Not transactional: the vendor call may take seconds, and no database connection should wait on
     * it. The reads run in their own read-only transactions and the result is written by
     * {@link AgentRoutineFireRecorder} in its own.
     */
    @Override
    public Optional<RoutineFireResult> fire(UUID organizationId, UUID userId, String ticketKey) {
        Optional<User> found = userQueryService.findAgent(organizationId, userId);
        if (found.isEmpty()) {
            // Most assignees are people; this is the normal path for them.
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_FIRE_SKIPPED,
                    organizationId, userId, AgentRoutineServiceLogConstant.SKIP_NOT_AGENT);
            return Optional.empty();
        }
        User agent = found.get();
        UserAgentDetail detail = agent.getAgentDetail();
        if (agent.getStatus() != UserStatus.ACTIVE) {
            return skip(organizationId, userId, AgentRoutineServiceLogConstant.SKIP_NOT_ACTIVE);
        }
        if (detail.getFlow() != AgentFlow.ROUTINE) {
            return skip(organizationId, userId, AgentRoutineServiceLogConstant.SKIP_NOT_ROUTINE_FLOW);
        }
        Optional<RoutineProvider> routineProvider = agentFlowHelper.routineProviderOf(detail.getProvider());
        if (routineProvider.isEmpty()) {
            return skip(organizationId, userId, AgentRoutineServiceLogConstant.SKIP_NO_ROUTINE_VENDOR);
        }
        Optional<AgentRoutine> foundRoutine = agentRoutineQueryService.find(organizationId, userId);
        if (foundRoutine.isEmpty()) {
            return skip(organizationId, userId, AgentRoutineServiceLogConstant.SKIP_NO_ROUTINE);
        }

        AgentRoutine routine = foundRoutine.get();
        RoutineProvider provider = routineProvider.get();
        Instant firedAt = Instant.now(clock);
        RoutineFireResult result;
        try {
            String secret = aesEncryptionService.decrypt(routine.getRoutineSecretEncrypted());
            result = routineService.fire(provider, routine.getRoutineId(), secret, ticketKey);
        } catch (RuntimeException exception) {
            // A secret that no longer decrypts, or a connector fault, is a failed fire: it is logged
            // here and recorded on the routine, where the Agents page shows it to an ADMIN.
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_FIRE_ERROR,
                    exception, organizationId, userId, provider, routine.getRoutineId());
            result = RoutineFireResult.failedWithoutAnswer();
        }

        if (result.status() == RoutineFireStatus.FIRED) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_FIRED,
                    organizationId, userId, provider, routine.getRoutineId(), result.status(), result.httpStatus());
        } else {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_FIRE_FAILED,
                    organizationId, userId, provider, routine.getRoutineId(), result.status(), result.httpStatus());
        }
        agentRoutineFireRecorder.record(routine.getId(), result, firedAt);
        return Optional.of(result);
    }

    /**
     * @return the routine vendor of a ROUTINE agent
     * @throws ValidationException AGT_VAL_003 for an API agent, which Oba runs itself and which so has
     *         no routine
     */
    private RoutineProvider routineProviderOrRefuse(UUID organizationId, User agent) {
        UserAgentDetail detail = agent.getAgentDetail();
        Optional<RoutineProvider> provider = agentFlowHelper.routineProviderOf(detail.getProvider());
        if (detail.getFlow() == AgentFlow.ROUTINE && provider.isPresent()) {
            return provider.get();
        }
        loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_FLOW_REFUSED,
                organizationId, agent.getId(), detail.getFlow());
        Map<String, Object> values = new HashMap<>();
        values.put(AgentConstant.FIELD_AGENT_ID, agent.getId().toString());
        values.put(AgentConstant.FIELD_FLOW, detail.getFlow().name());
        values.put(AgentConstant.FIELD_PROVIDER, detail.getProvider().name());
        throw new ValidationException(ExceptionDomainAgentError.VAL_FLOW_PROVIDER_UNSUPPORTED, values);
    }

    /**
     * The connector answers a malformed id with its own code; the agent API answers AGT_VAL_002, so a
     * client handles one set of agent codes. Any other refusal is passed on unchanged.
     */
    private void ensureValidRoutineId(UUID organizationId, UUID agentId, User agent, RoutineProvider provider,
            String routineId) {
        try {
            routineValidator.ensureValidRoutineId(provider, routineId);
        } catch (ValidationException exception) {
            if (!ExceptionDomainRoutineError.VAL_ROUTINE_ID_FORMAT.getCode().equals(exception.getErrorCode())) {
                throw exception;
            }
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_ID_REFUSED,
                    organizationId, agentId, agent.getAgentDetail().getProvider());
            Map<String, Object> values = new HashMap<>();
            values.put(AgentConstant.FIELD_AGENT_ID, agentId.toString());
            values.put(AgentConstant.FIELD_PROVIDER, agent.getAgentDetail().getProvider().name());
            throw new ValidationException(ExceptionDomainAgentError.VAL_ROUTINE_ID_FORMAT, values);
        }
    }

    private Optional<RoutineFireResult> skip(UUID organizationId, UUID userId, String reason) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, AgentRoutineServiceLogConstant.AGENT_ROUTINE_FIRE_SKIPPED,
                organizationId, userId, reason);
        return Optional.empty();
    }

    /**
     * At most half of the secret, so a short secret is never shown whole.
     */
    private static String secretPrefix(String secret) {
        int length = Math.min(AgentRoutineConstant.SECRET_PREFIX_LENGTH, secret.length() / 2);
        return secret.substring(0, length);
    }

    private static Map<String, Object> describe(AgentRoutine routine) {
        Map<String, Object> values = new HashMap<>();
        values.put(AgentRoutineConstant.FIELD_AGENT_ROUTINE_ID, String.valueOf(routine.getId()));
        values.put(AgentRoutineConstant.FIELD_ROUTINE_ID, routine.getRoutineId());
        return values;
    }

    private void audit(UUID organizationId, User actor, String action, UUID agentId,
            Map<String, Object> oldValues, Map<String, Object> newValues) {
        auditService.log(auditLogMapper.toRequest(organizationId, actor.getId(), action,
                AgentConstant.AUDIT_ENTITY_TYPE, agentId, oldValues, newValues));
    }
}
