package com.korur.oba.connect.routine.core.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// 2. Third-party
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.core.client.RoutineClient;
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.connect.routine.core.router.RoutineRouter;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class RoutineServiceImplUnitTest {

    private static final String ROUTINE_ID = "trig_abc123";

    @Mock
    private RoutineRouter routineRouter;

    @Mock
    private RoutineClient routineClient;

    @Mock
    private LoggingService loggingService;

    private RoutineServiceImpl routineService;

    @BeforeEach
    void setUp() {
        routineService = new RoutineServiceImpl(routineRouter, loggingService);
        when(routineRouter.resolveClient(RoutineProvider.ANTHROPIC)).thenReturn(routineClient);
    }

    @Test
    void fire_clientAnswers_returnsTheClientResult() {
        // Arrange
        RoutineFireResult answer = new RoutineFireResult(RoutineFireStatus.FIRED, 200, "https://claude.ai/s/1");
        when(routineClient.fire(ROUTINE_ID, "secret", "KAZ-1")).thenReturn(answer);

        // Act
        RoutineFireResult result = routineService.fire(RoutineProvider.ANTHROPIC, ROUTINE_ID, "secret", "KAZ-1");

        // Assert
        assertThat(result).isEqualTo(answer);
    }
}
