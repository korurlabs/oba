package com.korur.oba.connect.routine.anthropic.client;

// 1. Framework-level
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.anthropic.constant.AnthropicRoutineConstant;
import com.korur.oba.connect.routine.anthropic.dto.api.AnthropicApiRoutineFireRequest;
import com.korur.oba.connect.routine.anthropic.dto.api.AnthropicApiRoutineFireResponse;
import com.korur.oba.connect.routine.core.client.RoutineClient;
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.routine.anthropic.constant.client.AnthropicRoutineClientLogConstant.*;

/**
 * Fires Anthropic routines (Claude Code routines with an API trigger).
 *
 * Each routine has its own fire secret, sent as a bearer token and never logged. The answer maps to a
 * {@link RoutineFireStatus}: a 2xx to {@link RoutineFireStatus#FIRED} with the session link, 401 to
 * {@link RoutineFireStatus#UNAUTHORIZED} (the secret was rotated in claude.ai), 429 to
 * {@link RoutineFireStatus#RATE_LIMITED}, every other status and a missing answer to
 * {@link RoutineFireStatus#FAILED}. A fire is never retried here: Anthropic counts fires against an
 * hourly limit, and a second session for the same ticket would do the work twice.
 */
public class AnthropicRoutineClient implements RoutineClient {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AnthropicRoutineClient.class);

    private final RestClient restClient;
    private final LoggingService loggingService;

    /**
     * Creates the client.
     *
     * @param restClient     a client with the Anthropic base URL, its version headers and timeouts
     * @param loggingService the logging service
     */
    public AnthropicRoutineClient(RestClient restClient, LoggingService loggingService) {
        this.restClient = restClient;
        this.loggingService = loggingService;
    }

    @Override
    public RoutineProvider provider() {
        return RoutineProvider.ANTHROPIC;
    }

    @Override
    public boolean isValidRoutineId(String routineId) {
        return routineId != null && AnthropicRoutineConstant.ROUTINE_ID_PATTERN.matcher(routineId).matches();
    }

    @Override
    public RoutineFireResult fire(String routineId, String secret, String text) {
        if (!isValidRoutineId(routineId)) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, FIRE_SKIPPED_INVALID_ID);
            return RoutineFireResult.failedWithoutAnswer();
        }
        try {
            ResponseEntity<AnthropicApiRoutineFireResponse> response = restClient.post()
                    .uri(AnthropicRoutineConstant.FIRE_PATH, routineId)
                    .headers(headers -> headers.setBearerAuth(secret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AnthropicApiRoutineFireRequest(text))
                    .retrieve()
                    .toEntity(AnthropicApiRoutineFireResponse.class);
            AnthropicApiRoutineFireResponse body = response.getBody();
            String sessionUrl = body == null ? null : body.claudeCodeSessionUrl();
            loggingService.info(LOG_PREFIX, LogLayer.CLIENT, FIRE_ACCEPTED, routineId, sessionUrl);
            return new RoutineFireResult(RoutineFireStatus.FIRED, response.getStatusCode().value(), sessionUrl);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            RoutineFireStatus fireStatus = statusOf(status);
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, FIRE_REFUSED, routineId, status, fireStatus);
            return new RoutineFireResult(fireStatus, status, null);
        } catch (RestClientException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, FIRE_NO_ANSWER, exception, routineId);
            return RoutineFireResult.failedWithoutAnswer();
        }
    }

    private static RoutineFireStatus statusOf(int status) {
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return RoutineFireStatus.UNAUTHORIZED;
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return RoutineFireStatus.RATE_LIMITED;
        }
        return RoutineFireStatus.FAILED;
    }
}
