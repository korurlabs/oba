package com.korur.oba.connect.retry.aspect;

// 1. Framework-level
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 4. Internal
import com.korur.oba.connect.retry.annotation.ConnectRetry;
import com.korur.oba.connect.retry.constant.ConnectRetryLogConstant;
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;
import com.korur.oba.connect.retry.service.RetryEventService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Purpose:
 * AOP aspect that intercepts methods annotated with {@code @ConnectRetry}
 * and wraps them in a retry loop with exponential backoff.
 *
 * Bigger picture:
 * Provides a centralized, annotation-driven retry mechanism for all connect
 * layer integrations. The retry configuration is pulled from {@link ConnectRetryPolicy},
 * keeping each method declaration clean and the policy centralized in the enum.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-03-04
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ConnectRetryAspect {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ConnectRetryAspect.class);
    private static final long MAX_DELAY_MS = 30_000L;

    private final LoggingService loggingService;
    private final RetryEventService retryEventService;

    @Around("@annotation(connectRetry)")
    public Object retry(ProceedingJoinPoint joinPoint, ConnectRetry connectRetry) throws Throwable {
        ConnectRetryPolicy policy = connectRetry.value();
        String methodName = resolveMethodName(joinPoint);

        RetryTemplateBuilder builder = RetryTemplate.builder()
                .maxAttempts(policy.getMaxAttempts())
                .exponentialBackoff(policy.getDelayMs(), policy.getMultiplier(), MAX_DELAY_MS);

        if (policy.getNoRetryFor().length > 0) {
            for (Class<? extends Throwable> nonRetryable : policy.getNoRetryFor()) {
                builder.notRetryOn(nonRetryable);
            }
        } else {
            for (Class<? extends Throwable> retryable : policy.getRetryFor()) {
                builder.retryOn(retryable);
            }
        }

        RetryTemplate retryTemplate = builder.build();

        long start = System.currentTimeMillis();
        int[] attemptCount = {0};

        try {
            Object result = retryTemplate.execute(ctx -> {
                attemptCount[0] = ctx.getRetryCount() + 1;
                if (ctx.getRetryCount() > 0) {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                            ConnectRetryLogConstant.RETRY_ATTEMPT,
                            ctx.getRetryCount(), policy.getMaxAttempts(),
                            policy.getSystem().name());
                }
                try {
                    return joinPoint.proceed();
                } catch (Throwable t) {
                    if (t instanceof Exception ex) {
                        throw ex;
                    }
                    throw new RuntimeException(t);
                }
            });

            if (attemptCount[0] > 1) {
                long duration = System.currentTimeMillis() - start;
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                        ConnectRetryLogConstant.RETRY_SUCCESS_AFTER_RETRIES,
                        policy.getSystem().name(), attemptCount[0]);
                persistCompletedEvent(methodName, policy, attemptCount[0], duration);
            }

            return result;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    ConnectRetryLogConstant.RETRY_EXHAUSTED,
                    ex, policy.getSystem().name(), attemptCount[0]);
            persistExhaustedEvent(methodName, policy, attemptCount[0], duration, ex);
            throw ex;
        }
    }

    private void persistCompletedEvent(String methodName, ConnectRetryPolicy policy,
                                       int attempts, long durationMs) {
        try {
            retryEventService.createCompleted(methodName, policy, attempts, durationMs);
        } catch (Exception persistEx) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    ConnectRetryLogConstant.RETRY_EVENT_PERSIST_FAILED,
                    persistEx, methodName, policy.getSystem().name());
        }
    }

    private void persistExhaustedEvent(String methodName, ConnectRetryPolicy policy,
                                       int attempts, long durationMs, Exception ex) {
        try {
            retryEventService.createExhaustedWithDeadLetter(methodName, policy, attempts, durationMs, ex);
        } catch (Exception persistEx) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    ConnectRetryLogConstant.RETRY_EVENT_PERSIST_FAILED,
                    persistEx, methodName, policy.getSystem().name());
        }
    }

    private String resolveMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }
}
