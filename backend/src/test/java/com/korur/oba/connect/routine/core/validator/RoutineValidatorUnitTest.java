package com.korur.oba.connect.routine.core.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 2. Third-party
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.core.client.RoutineClient;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.connect.routine.core.router.RoutineRouter;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class RoutineValidatorUnitTest {

    private static final String ROUTINE_ID = "trig_abc123";

    @Mock
    private RoutineRouter routineRouter;

    @Mock
    private RoutineClient routineClient;

    @Mock
    private LoggingService loggingService;

    private RoutineValidator routineValidator;

    @BeforeEach
    void setUp() {
        routineValidator = new RoutineValidator(routineRouter, loggingService);
        when(routineRouter.resolveClient(RoutineProvider.ANTHROPIC)).thenReturn(routineClient);
    }

    @Test
    void ensureValidRoutineId_wrongFormat_throwsValidationException() {
        // Arrange
        when(routineClient.isValidRoutineId("bad")).thenReturn(false);

        // Act and assert
        assertThatThrownBy(() -> routineValidator.ensureValidRoutineId(RoutineProvider.ANTHROPIC, "bad"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "RTN_VAL_001");
    }

    @Test
    void ensureValidRoutineId_rightFormat_passes() {
        // Arrange
        when(routineClient.isValidRoutineId(ROUTINE_ID)).thenReturn(true);

        // Act and assert
        assertThatCode(() -> routineValidator.ensureValidRoutineId(RoutineProvider.ANTHROPIC, ROUTINE_ID))
                .doesNotThrowAnyException();
    }
}
