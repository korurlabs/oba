package com.korur.oba.connect.outbox.aspect;

// 1. Framework-level
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.outbox.annotation.ConnectOutbox;
import com.korur.oba.connect.outbox.constant.aspect.ConnectOutboxAspectLogConstant;
import com.korur.oba.connect.outbox.dto.OutboxCreateDTO;
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.enums.ConnectOutboxPolicy;
import com.korur.oba.connect.outbox.enums.OutboxEventType;
import com.korur.oba.connect.outbox.service.OutboxEventService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Purpose:
 * AOP aspect that intercepts methods annotated with {@code @ConnectOutbox}
 * and enqueues the method arguments as a transactional outbox event.
 *
 * Bigger picture:
 * Provides a declarative, annotation-driven mechanism for fire-and-forget
 * operations. Instead of executing the method body immediately, it serializes
 * the arguments into a JSON payload and creates an {@code OutboxEvent} that
 * the scheduler will pick up and dispatch to the matching handler.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-03-04
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ConnectOutboxAspect {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ConnectOutboxAspect.class);

    private final OutboxEventService outboxEventService;
    private final LoggingService loggingService;

    @Around("@annotation(connectOutbox)")
    public Object intercept(ProceedingJoinPoint joinPoint, ConnectOutbox connectOutbox) {
        ConnectOutboxPolicy policy = connectOutbox.value();
        OutboxEventType eventType = connectOutbox.eventType();

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                ConnectOutboxAspectLogConstant.OUTBOX_EVENT_ENQUEUING,
                policy.getSystem().name(), eventType.name());

        Object payload = buildPayload(joinPoint);

        OutboxEvent outboxEvent = outboxEventService.create(new OutboxCreateDTO(
                payload,
                eventType,
                policy.getSystem()));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                ConnectOutboxAspectLogConstant.OUTBOX_EVENT_ENQUEUED,
                policy.getSystem().name(), eventType.name(), outboxEvent.getId());

        return null;
    }

    /**
     * Builds a map of parameter names to values from the join point.
     */
    private Map<String, Object> buildPayload(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {
            payload.put(parameterNames[i], args[i]);
        }
        return payload;
    }
}
