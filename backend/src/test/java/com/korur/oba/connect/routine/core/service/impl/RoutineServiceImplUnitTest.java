package com.korur.oba.connect.routine.core.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.korur.oba.dojo.exception.internal.business.ValidationException;
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
    void isAvailable_clientSwitchedOff_returnsFalse() {
        // Arrange
        when(routineClient.isEnabled()).thenReturn(false);

        // Act and assert
        assertThat(routineService.isAvailable(RoutineProvider.ANTHROPIC)).isFalse();
    }

    @Test
    void isAvailable_clientSwitchedOn_returnsTrue() {
        // Arrange
        when(routineClient.isEnabled()).thenReturn(true);

        // Act and assert
        assertThat(routineService.isAvailable(RoutineProvider.ANTHROPIC)).isTrue();
    }

    @Test
    void validateRoutineId_wrongFormat_throwsValidationException() {
        // Arrange
        when(routineClient.isValidRoutineId("bad")).thenReturn(false);

        // Act and assert
        assertThatThrownBy(() -> routineService.validateRoutineId(RoutineProvider.ANTHROPIC, "bad"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "RTN_VAL_001");
    }

    @Test
    void validateRoutineId_rightFormat_passes() {
        // Arrange
        when(routineClient.isValidRoutineId(ROUTINE_ID)).thenReturn(true);

        // Act and assert
        assertThatCode(() -> routineService.validateRoutineId(RoutineProvider.ANTHROPIC, ROUTINE_ID))
                .doesNotThrowAnyException();
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
