package com.korur.oba.connect.retry.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.connect.retry.constant.service.RetryDeadEventServiceLogConstant;
import com.korur.oba.connect.retry.constant.service.RetryEventServiceLogConstant;
import com.korur.oba.connect.retry.entity.RetryDeadEvent;
import com.korur.oba.connect.retry.entity.RetryEvent;
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;
import com.korur.oba.connect.retry.enums.RetryEventStatus;
import com.korur.oba.connect.retry.repository.RetryDeadEventRepository;
import com.korur.oba.connect.retry.repository.RetryEventRepository;
import com.korur.oba.connect.retry.service.RetryEventService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Persists retry event records and dead letter records for exhausted retries.
 *
 * <p>Uses {@code REQUIRES_NEW} propagation so persistence does not interfere
 * with the caller's transaction (which may be rolling back on exhaustion).</p>
 */
@Service
@RequiredArgsConstructor
public class RetryEventServiceImpl implements RetryEventService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RetryEventServiceImpl.class);

    private final RetryEventRepository retryEventRepository;
    private final RetryDeadEventRepository retryDeadEventRepository;
    private final LoggingService loggingService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createCompleted(String methodName, ConnectRetryPolicy policy,
                                int attempts, long durationMs) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                RetryEventServiceLogConstant.RETRY_EVENT_CREATING_COMPLETED,
                methodName, policy.getSystem().name(), attempts);

        RetryEvent retryEvent = RetryEvent.builder()
                .methodName(methodName)
                .connectRetrySystem(policy.getSystem())
                .attempts(attempts)
                .maxAttempts(policy.getMaxAttempts())
                .status(RetryEventStatus.COMPLETED)
                .durationMs(durationMs)
                .build();
        retryEvent = retryEventRepository.save(retryEvent);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                RetryEventServiceLogConstant.RETRY_EVENT_CREATED_COMPLETED, retryEvent.getId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createExhaustedWithDeadLetter(String methodName, ConnectRetryPolicy policy,
                                              int attempts, long durationMs, Exception ex) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                RetryEventServiceLogConstant.RETRY_EVENT_CREATING_EXHAUSTED,
                methodName, policy.getSystem().name(), attempts);

        RetryEvent retryEvent = RetryEvent.builder()
                .methodName(methodName)
                .connectRetrySystem(policy.getSystem())
                .attempts(attempts)
                .maxAttempts(policy.getMaxAttempts())
                .status(RetryEventStatus.EXHAUSTED)
                .lastErrorMessage(ex.getMessage())
                .lastErrorStacktrace(ExceptionUtils.getStackTrace(ex))
                .durationMs(durationMs)
                .build();
        retryEvent = retryEventRepository.save(retryEvent);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                RetryDeadEventServiceLogConstant.DEAD_LETTER_CREATING,
                methodName, policy.getSystem().name());

        RetryDeadEvent deadEvent = RetryDeadEvent.builder()
                .originalRetryEventId(retryEvent.getId())
                .methodName(methodName)
                .connectRetrySystem(policy.getSystem())
                .attempts(attempts)
                .lastErrorMessage(ex.getMessage())
                .lastErrorStacktrace(ExceptionUtils.getStackTrace(ex))
                .failedAt(Instant.now())
                .build();
        deadEvent = retryDeadEventRepository.save(deadEvent);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                RetryEventServiceLogConstant.RETRY_EVENT_CREATED_EXHAUSTED,
                retryEvent.getId(), deadEvent.getId());
    }
}
