package com.korur.oba.dojo.logging.service.unit;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 2. Third-party
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.constant.LoggingConstant;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Nested
    class LineBreakNeutralisation {

        private static final String FORGED_ENTRY = "ok\r\n12:00:00.000 INFO  [main] forged - admin signed in";

        private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(LoggingService.class);

        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private Level originalLevel;

        private boolean originalAdditive;

        @BeforeEach
        void attachAppender() {
            originalLevel = serviceLogger.getLevel();
            originalAdditive = serviceLogger.isAdditive();
            serviceLogger.setLevel(Level.TRACE);
            serviceLogger.setAdditive(false);
            appender.start();
            serviceLogger.addAppender(appender);
        }

        @AfterEach
        void detachAppender() {
            serviceLogger.detachAppender(appender);
            appender.stop();
            serviceLogger.setLevel(originalLevel);
            serviceLogger.setAdditive(originalAdditive);
        }

        @Test
        void info_argumentWithCrLf_writesOneLineWithUnderscores() {
            // Act
            loggingService.info(TEST_PREFIX, LogLayer.SERVICE, "Uploaded {}", FORGED_ENTRY);

            // Assert
            ILoggingEvent event = singleEvent();
            assertEquals("Uploaded ok__12:00:00.000 INFO  [main] forged - admin signed in",
                    event.getFormattedMessage());
        }

        @Test
        void warn_messageWithCrLf_writesOneLine() {
            // Act
            loggingService.warn(TEST_PREFIX, LogLayer.SERVICE, FORGED_ENTRY);

            // Assert
            assertSingleLine(singleEvent().getFormattedMessage());
        }

        @Test
        void error_withThrowableAndArgumentsWithLineSeparators_writesOneLineAndKeepsCause() {
            // Arrange
            RuntimeException cause = new RuntimeException("boom");

            // Act
            loggingService.error(TEST_PREFIX, LogLayer.HANDLER, "Failed {} for {}", cause,
                    "a\u2028b", "c\u0085d\u2029e");

            // Assert
            ILoggingEvent event = singleEvent();
            assertEquals("Failed a_b for c_d_e", event.getFormattedMessage());
            assertNotNull(event.getThrowableProxy());
            assertEquals("boom", event.getThrowableProxy().getMessage());
        }

        @Test
        void error_withoutThrowableArgumentWithControlCharacters_writesOneLine() {
            // Act
            loggingService.error(TEST_PREFIX, LogLayer.HANDLER, "Header {}", "x\u000By\u000Cz\u001B[31m");

            // Assert
            assertEquals("Header x_y_z_[31m", singleEvent().getFormattedMessage());
        }

        @Test
        void observe_nonStringArgumentWithLineBreaks_writesOneLine() {
            // Arrange
            Object argument = new Object() {
                @Override
                public String toString() {
                    return FORGED_ENTRY;
                }
            };

            // Act
            loggingService.observe(TEST_PREFIX, LogLayer.CONTROLLER, "Body {}", argument);

            // Assert
            assertSingleLine(singleEvent().getFormattedMessage());
        }

        @Test
        void debug_arrayArgumentWithLineBreaks_writesOneLine() {
            // Act
            loggingService.debug(TEST_PREFIX, LogLayer.SERVICE, "Values {}",
                    (Object) new String[] {"a\nb", "c"});

            // Assert
            assertEquals("Values [a_b, c]", singleEvent().getFormattedMessage());
        }

        @Test
        void warn_trailingThrowableArgument_keepsItAsCause() {
            // Arrange
            IllegalStateException cause = new IllegalStateException("unreadable");

            // Act
            loggingService.warn(TEST_PREFIX, LogLayer.SERVICE, "Image {} unreadable", "a\nb.png", cause);

            // Assert
            ILoggingEvent event = singleEvent();
            assertEquals("Image a_b.png unreadable", event.getFormattedMessage());
            assertNotNull(event.getThrowableProxy());
            assertEquals("unreadable", event.getThrowableProxy().getMessage());
        }

        @Test
        void info_nullArgument_rendersNull() {
            // Act
            loggingService.info(TEST_PREFIX, LogLayer.SERVICE, "Value {}", (Object) null);

            // Assert
            assertEquals("Value null", singleEvent().getFormattedMessage());
        }

        @Test
        void info_levelDisabled_writesNothing() {
            // Arrange
            serviceLogger.setLevel(Level.WARN);

            // Act
            loggingService.info(TEST_PREFIX, LogLayer.SERVICE, "Value {}", FORGED_ENTRY);

            // Assert
            assertTrue(appender.list.isEmpty());
        }

        private ILoggingEvent singleEvent() {
            List<ILoggingEvent> events = appender.list;
            assertEquals(1, events.size(), "Exactly one log event must be written");
            ILoggingEvent event = events.get(0);
            assertSingleLine(event.getFormattedMessage());
            return event;
        }

        private void assertSingleLine(String formattedMessage) {
            assertFalse(formattedMessage.matches("(?s).*[\\r\\n\\u0085\\u2028\\u2029].*"),
                    "The formatted message must not contain a line separator: " + formattedMessage);
        }
    }
}
