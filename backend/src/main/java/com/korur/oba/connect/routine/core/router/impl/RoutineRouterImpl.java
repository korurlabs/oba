package com.korur.oba.connect.routine.core.router.impl;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.routine.core.client.RoutineClient;
import com.korur.oba.connect.routine.core.constant.RoutineConstant;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.connect.routine.core.exception.ExceptionDomainRoutineError;
import com.korur.oba.connect.routine.core.router.RoutineRouter;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.routine.core.constant.router.RoutineRouterLogConstant.*;

/**
 * Maps every {@link RoutineProvider} to the one {@link RoutineClient} bean that serves it.
 *
 * The map is built and checked when the bean is created: a provider without a client, or with two,
 * stops the application at start. A vendor value therefore never ships without its client, and a
 * fire never meets a missing client at run time.
 */
@Component
public class RoutineRouterImpl implements RoutineRouter {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RoutineRouterImpl.class);

    private final Map<RoutineProvider, RoutineClient> clients;

    /**
     * Collects the clients and checks that each provider has exactly one.
     *
     * @param routineClients every {@link RoutineClient} bean
     * @param loggingService the logging service
     * @throws InternalServerErrorException when a provider has no client or two
     */
    public RoutineRouterImpl(List<RoutineClient> routineClients, LoggingService loggingService) {
        Map<RoutineProvider, RoutineClient> byProvider = new EnumMap<>(RoutineProvider.class);
        for (RoutineClient client : routineClients) {
            RoutineClient previous = byProvider.putIfAbsent(client.provider(), client);
            if (previous != null) {
                loggingService.error(LOG_PREFIX, LogLayer.CONFIG, CLIENT_DUPLICATE,
                        previous.getClass().getSimpleName(), client.getClass().getSimpleName(), client.provider());
                throw new InternalServerErrorException(ExceptionDomainRoutineError.INT_CLIENT_DUPLICATE,
                        Map.of(RoutineConstant.FIELD_PROVIDER, client.provider().name()));
            }
        }
        for (RoutineProvider provider : RoutineProvider.values()) {
            if (!byProvider.containsKey(provider)) {
                loggingService.error(LOG_PREFIX, LogLayer.CONFIG, CLIENT_MISSING, provider);
                throw new InternalServerErrorException(ExceptionDomainRoutineError.INT_CLIENT_MISSING,
                        Map.of(RoutineConstant.FIELD_PROVIDER, provider.name()));
            }
        }
        this.clients = byProvider;
        loggingService.info(LOG_PREFIX, LogLayer.CONFIG, CLIENTS_REGISTERED, byProvider.keySet());
    }

    @Override
    public RoutineClient resolveClient(RoutineProvider provider) {
        return clients.get(provider);
    }
}
