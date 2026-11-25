package com.korur.oba.shrine.agent.routine.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.routine.constant.service.AgentRoutineServiceLogConstant;
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;
import com.korur.oba.shrine.agent.routine.repository.AgentRoutineRepository;

/**
 * Stores the result of a routine fire in its own transaction. The vendor call happens outside any
 * transaction, so no database connection waits on it, and the result is kept whatever happens to the
 * caller: it is how an ADMIN sees that a fire failed.
 */
@Component
@RequiredArgsConstructor
public class AgentRoutineFireRecorder {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AgentRoutineFireRecorder.class);

    private final AgentRoutineRepository agentRoutineRepository;
    private final LoggingService loggingService;

    /**
     * @param agentRoutineId the routine row that was fired
     * @param result         what the vendor answered
     * @param firedAt        when the fire was sent
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID agentRoutineId, RoutineFireResult result, Instant firedAt) {
        Optional<AgentRoutine> found = agentRoutineRepository.findById(agentRoutineId);
        if (found.isEmpty()) {
            // The routine was removed while the fire was in flight; there is nothing left to show it on.
            loggingService.warn(LOG_PREFIX, LogLayer.HELPER, AgentRoutineServiceLogConstant.AGENT_ROUTINE_RECORD_MISSING,
                    agentRoutineId, result.status());
            return;
        }
        AgentRoutine routine = found.get();
        routine.setLastFiredAt(firedAt);
        routine.setLastFireStatus(result.status());
        routine.setLastHttpStatus(result.httpStatus());
        routine.setLastSessionUrl(result.sessionUrl());
        agentRoutineRepository.save(routine);
        loggingService.info(LOG_PREFIX, LogLayer.HELPER, AgentRoutineServiceLogConstant.AGENT_ROUTINE_FIRE_RECORDED,
                agentRoutineId, result.status());
    }
}
