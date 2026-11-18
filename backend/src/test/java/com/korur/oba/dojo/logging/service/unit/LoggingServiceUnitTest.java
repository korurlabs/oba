package com.korur.oba.dojo.logging.service.unit;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 2. Third-party
import org.slf4j.MDC;

// 4. Internal
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.constant.LoggingConstant;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoggingServiceUnitTest {

    private static final LogPrefix TEST_PREFIX = LogPrefix.fromClass(LoggingServiceUnitTest.class);

    private final LoggingService loggingService = new LoggingService();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    class Info {

        @Test
        void nullPrefix_throwsInternalServerErrorException() {
            // Act & Assert
            assertThrows(InternalServerErrorException.class,
                    () -> loggingService.info(null, LogLayer.SERVICE, "test message"));
        }

        @Test
        void nullLayer_throwsInternalServerErrorException() {
            // Act & Assert
            assertThrows(InternalServerErrorException.class,
                    () -> loggingService.info(TEST_PREFIX, null, "test message"));
        }

        @Test
        void validInput_logsSuccessfully() {
            // Act & Assert
            assertDoesNotThrow(() -> loggingService.info(TEST_PREFIX, LogLayer.SERVICE, "test message"));
        }

        @Test
        void validInput_clearsMdcAfterLogging() {
            // Act
            loggingService.info(TEST_PREFIX, LogLayer.SERVICE, "test message");

            // Assert — MDC domain and layer should be cleared after the call
            assertNull(MDC.get(LoggingConstant.KEY_DOMAIN));
            assertNull(MDC.get(LoggingConstant.KEY_LAYER));
        }
    }

    @Nested
    class Observe {

        @Test
        void nullPrefix_throwsInternalServerErrorException() {
            // Act & Assert
            assertThrows(InternalServerErrorException.class,
                    () -> loggingService.observe(null, LogLayer.CONTROLLER, "test message"));
        }

        @Test
        void nullLayer_throwsInternalServerErrorException() {
            // Act & Assert
            assertThrows(InternalServerErrorException.class,
                    () -> loggingService.observe(TEST_PREFIX, null, "test message"));
        }

        @Test
        void validInput_logsSuccessfully() {
            // Act & Assert
            assertDoesNotThrow(() -> loggingService.observe(TEST_PREFIX, LogLayer.CONTROLLER, "observe message"));
        }
    }

    @Nested
    class Warn {

        @Test
        void validInput_logsSuccessfully() {
            // Act & Assert
            assertDoesNotThrow(() -> loggingService.warn(TEST_PREFIX, LogLayer.SERVICE, "warning message"));
        }
    }

    @Nested
    class Error {

        @Test
        void withThrowable_validInput_logsSuccessfully() {
            // Arrange
            Throwable throwable = new RuntimeException("test error");

            // Act & Assert
            assertDoesNotThrow(() -> loggingService.error(
                    TEST_PREFIX, LogLayer.HANDLER, "error message", throwable));
        }

        @Test
        void withoutThrowable_validInput_logsSuccessfully() {
            // Act & Assert
            assertDoesNotThrow(() -> loggingService.error(
                    TEST_PREFIX, LogLayer.HANDLER, "error message"));
        }
    }

    @Nested
    class Debug {

        @Test
        void validInput_logsSuccessfully() {
            // Act & Assert
            assertDoesNotThrow(() -> loggingService.debug(TEST_PREFIX, LogLayer.SERVICE, "debug message"));
        }
    }
}
