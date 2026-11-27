package com.korur.oba.dojo.exception.handler.mvc_layer;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// 3. Java SDK
import java.time.Duration;
import java.util.Map;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.dojo.exception.enums.ExceptionUpstreamError;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.exception.internal.infrastructure.ServiceUnavailableException;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerUnitTest {

    @Mock
    private LoggingService loggingService;

    private ResponseEntity<CustomResponse<Void>> tooManyRequests(Duration retryAfter) {
        return new GlobalExceptionHandler(loggingService).handleApplicationException(
                new TooManyRequestsException(ExceptionApiError.TOO_MANY_REQUESTS, Map.of(), retryAfter));
    }

    @Nested
    class RetryAfter {

        @Test
        void wholeSeconds_areSentAsTheyAre() {
            ResponseEntity<CustomResponse<Void>> response = tooManyRequests(Duration.ofSeconds(60));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertEquals("60", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        }

        @Test
        void partOfASecond_isRoundedUpSoTheLimitHasFreedWhenTheClientComesBack() {
            assertEquals("40", tooManyRequests(Duration.ofMillis(39_600)).getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
            assertEquals("1", tooManyRequests(Duration.ofMillis(1)).getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        }

        @Test
        void noWait_isSentAsOneSecond() {
            assertEquals("1", tooManyRequests(Duration.ZERO).getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        }

        @Test
        void serviceUnavailable_carriesItsDelayAndStaysJson() {
            ResponseEntity<CustomResponse<Void>> response = new GlobalExceptionHandler(loggingService).handleApplicationException(
                    new ServiceUnavailableException(ExceptionUpstreamError.SERVER_BUSY, Duration.ofSeconds(1)));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
            assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
            assertEquals(ExceptionUpstreamError.SERVER_BUSY.getCode(), response.getBody().error().code());
        }

        @Test
        void refusalWithoutADelay_carriesNoRetryAfter() {
            ResponseEntity<CustomResponse<Void>> response = new GlobalExceptionHandler(loggingService).handleApplicationException(
                    new TooManyRequestsException(ExceptionApiError.TOO_MANY_REQUESTS));

            assertFalse(response.getHeaders().containsKey(HttpHeaders.RETRY_AFTER));
        }
    }
}
