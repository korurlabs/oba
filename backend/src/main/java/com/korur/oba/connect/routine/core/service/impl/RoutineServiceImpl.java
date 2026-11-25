package com.korur.oba.connect.routine.core.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.core.client.RoutineClient;
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.connect.routine.core.router.RoutineRouter;
import com.korur.oba.connect.routine.core.service.RoutineService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.routine.core.constant.service.RoutineServiceLogConstant.*;

/**
 * Fires hosted routines through the client the {@link RoutineRouter} resolves for the vendor.
 */
@Service
@RequiredArgsConstructor
public class RoutineServiceImpl implements RoutineService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RoutineServiceImpl.class);

    private final RoutineRouter routineRouter;
    private final LoggingService loggingService;

    @Override
    public RoutineFireResult fire(RoutineProvider provider, String routineId, String secret, String text) {
        RoutineClient client = routineRouter.resolveClient(provider);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, FIRING_ROUTINE, routineId, provider);
        RoutineFireResult result = client.fire(routineId, secret, text);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, ROUTINE_FIRE_ANSWERED, routineId, provider,
                result.status(), result.httpStatus());
        return result;
    }
}
