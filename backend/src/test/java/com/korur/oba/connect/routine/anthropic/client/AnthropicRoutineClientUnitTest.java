package com.korur.oba.connect.routine.anthropic.client;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 2. Third-party

// 3. Java SDK
import java.net.SocketTimeoutException;

// 4. Internal
import com.korur.oba.connect.routine.anthropic.properties.AnthropicRoutineProperties;
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.dojo.logging.service.LoggingService;

class AnthropicRoutineClientUnitTest {

    private static final String BASE_URL = "https://anthropic.test";
    private static final String ROUTINE_ID = "trig_01AbCdEf";
    private static final String FIRE_URL = BASE_URL + "/v1/claude_code/routines/" + ROUTINE_ID + "/fire";
    private static final String SECRET = "sk-ant-fire-secret";
    private static final String TICKET_KEY = "KAZ-42";
    private static final String SESSION_URL = "https://claude.ai/code/session_01";

    private MockRestServiceServer server;
    private AnthropicRoutineProperties properties;
    private AnthropicRoutineClient client;
    private final LoggingService loggingService = mock(LoggingService.class);

    @BeforeEach
    void setUp() {
        properties = new AnthropicRoutineProperties();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("anthropic-beta", properties.getBetaHeader())
                .defaultHeader("anthropic-version", properties.getVersion());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AnthropicRoutineClient(builder.build(), loggingService);
    }

    @Test
    void fire_ok_returnsFiredWithSessionUrl() {
        // Arrange
        server.expect(requestTo(FIRE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andExpect(header("anthropic-beta", "experimental-cc-routine-2026-04-01"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.text").value(TICKET_KEY))
                .andRespond(withSuccess("{\"type\":\"routine_fire\",\"claude_code_session_id\":\"session_01\","
                        + "\"claude_code_session_url\":\"" + SESSION_URL + "\"}", MediaType.APPLICATION_JSON));

        // Act
        RoutineFireResult result = client.fire(ROUTINE_ID, SECRET, TICKET_KEY);

        // Assert
        assertThat(result).isEqualTo(new RoutineFireResult(RoutineFireStatus.FIRED, 200, SESSION_URL));
        server.verify();
    }

    @Test
    void fire_unauthorized_returnsUnauthorized() {
        // Arrange
        server.expect(requestTo(FIRE_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // Act
        RoutineFireResult result = client.fire(ROUTINE_ID, SECRET, TICKET_KEY);

        // Assert
        assertThat(result).isEqualTo(new RoutineFireResult(RoutineFireStatus.UNAUTHORIZED, 401, null));
    }

    @Test
    void fire_tooManyRequests_returnsRateLimited() {
        // Arrange
        server.expect(requestTo(FIRE_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // Act
        RoutineFireResult result = client.fire(ROUTINE_ID, SECRET, TICKET_KEY);

        // Assert
        assertThat(result).isEqualTo(new RoutineFireResult(RoutineFireStatus.RATE_LIMITED, 429, null));
    }

    @Test
    void fire_serverError_returnsFailedWithStatus() {
        // Arrange
        server.expect(requestTo(FIRE_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act
        RoutineFireResult result = client.fire(ROUTINE_ID, SECRET, TICKET_KEY);

        // Assert
        assertThat(result).isEqualTo(new RoutineFireResult(RoutineFireStatus.FAILED, 500, null));
    }

    @Test
    void fire_timeout_returnsFailedWithoutStatus() {
        // Arrange
        server.expect(requestTo(FIRE_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        // Act
        RoutineFireResult result = client.fire(ROUTINE_ID, SECRET, TICKET_KEY);

        // Assert
        assertThat(result).isEqualTo(new RoutineFireResult(RoutineFireStatus.FAILED, null, null));
        verify(loggingService).error(any(), any(), anyString(), any(Throwable.class), any());
    }

    @Test
    void fire_malformedRoutineId_returnsFailedWithoutCall() {
        // Arrange
        server.expect(never(), requestTo(FIRE_URL));

        // Act
        RoutineFireResult result = client.fire("trig_../../admin", SECRET, TICKET_KEY);

        // Assert
        assertThat(result.status()).isEqualTo(RoutineFireStatus.FAILED);
        server.verify();
    }

    @Test
    void fire_anyAnswer_neverLogsTheSecret() {
        // Arrange
        server.expect(requestTo(FIRE_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // Act
        client.fire(ROUTINE_ID, SECRET, TICKET_KEY);

        // Assert
        assertThat(mockingDetails(loggingService).getInvocations())
                .isNotEmpty()
                .allSatisfy(invocation -> assertThat(invocation.getArguments())
                        .noneMatch(argument -> String.valueOf(argument).contains(SECRET)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"trig_a", "trig_01AbCdEf", "trig_012345678901234567890123456789012345678901234567890123456789"})
    void isValidRoutineId_anthropicFormat_returnsTrue(String routineId) {
        assertThat(client.isValidRoutineId(routineId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "trig_", "routine_abc", "trig_a/b", "trig_a-b", "TRIG_abc",
            "trig_0123456789012345678901234567890123456789012345678901234567890"})
    void isValidRoutineId_otherFormat_returnsFalse(String routineId) {
        assertThat(client.isValidRoutineId(routineId)).isFalse();
    }

    @Test
    void isValidRoutineId_null_returnsFalse() {
        assertThat(client.isValidRoutineId(null)).isFalse();
    }
}
