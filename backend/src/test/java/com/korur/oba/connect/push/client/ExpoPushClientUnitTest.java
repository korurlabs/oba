package com.korur.oba.connect.push.client;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 3. Java SDK
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.push.dto.expo.ExpoPushMessage;
import com.korur.oba.connect.push.dto.expo.ExpoPushReceipt;
import com.korur.oba.connect.push.dto.expo.ExpoPushTicket;
import com.korur.oba.connect.push.properties.ExpoProperties;
import com.korur.oba.connect.retry.annotation.ConnectRetry;
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;
import com.korur.oba.dojo.exception.external.InternalProcessException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.service.LoggingService;

class ExpoPushClientUnitTest {

    private static final String BASE_URL = "https://exp.test";
    private static final String SECRET = "expo-secret-token";

    private MockRestServiceServer server;
    private ExpoProperties properties;
    private ExpoPushClient client;
    private final LoggingService loggingService = mock(LoggingService.class);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new ExpoProperties();
        properties.setAccessToken(SECRET);
        client = new ExpoPushClient(builder.build(), properties, loggingService);
    }

    private static ExpoPushMessage message() {
        return new ExpoPushMessage("ExponentPushToken[a]", "t", "b", null, "default", "high", null, null, null,
                null, null);
    }

    @Test
    void isEnabled_blankToken_isFalse() {
        // Arrange
        properties.setAccessToken("  ");

        // Act and assert
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_tokenSet_isTrue() {
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    void send_okResponse_returnsTicketsInOrderWithTheBearerToken() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/--/api/v2/push/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andExpect(jsonPath("$[0].to").value("ExponentPushToken[a]"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"status\":\"ok\",\"id\":\"t1\"},"
                                + "{\"status\":\"error\",\"message\":\"x\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}",
                        MediaType.APPLICATION_JSON));

        // Act
        List<ExpoPushTicket> tickets = client.send(List.of(message(), message()));

        // Assert
        assertThat(tickets).hasSize(2);
        assertThat(tickets.get(0).id()).isEqualTo("t1");
        assertThat(tickets.get(1).details().error()).isEqualTo("DeviceNotRegistered");
        server.verify();
    }

    @Test
    void send_emptyBody_returnsNoTickets() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/--/api/v2/push/send"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // Act and assert
        assertThat(client.send(List.of(message()))).isEmpty();
    }

    @Test
    void send_serverError_throwsBadGatewayWithoutExpoBody() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/--/api/v2/push/send"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("ExponentPushToken[leak]"));

        // Act and assert
        assertThatThrownBy(() -> client.send(List.of(message())))
                .isInstanceOf(BadGatewayException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PSH_UPS_001")
                .hasMessageNotContaining("leak");
    }

    @Test
    void send_tooManyRequests_throwsBadGatewaySoItIsRetried() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/--/api/v2/push/send"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // Act and assert
        assertThatThrownBy(() -> client.send(List.of(message()))).isInstanceOf(BadGatewayException.class);
    }

    @Test
    void send_clientError_throwsInternalProcessWhichIsNotRetried() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/--/api/v2/push/send"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("bad token ExponentPushToken[leak]"));

        // Act and assert
        assertThatThrownBy(() -> client.send(List.of(message())))
                .isInstanceOf(InternalProcessException.class)
                .hasMessageNotContaining("leak");
    }

    @Test
    void send_unreadableBody_throwsInternalProcess() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/--/api/v2/push/send"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        // Act and assert
        assertThatThrownBy(() -> client.send(List.of(message()))).isInstanceOf(InternalProcessException.class);
    }

    @Test
    void getReceipts_okResponse_returnsReceiptsByTicketId() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/--/api/v2/push/getReceipts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids[0]").value("t1"))
                .andRespond(withSuccess(
                        "{\"data\":{\"t1\":{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}}}",
                        MediaType.APPLICATION_JSON));

        // Act
        Map<String, ExpoPushReceipt> receipts = client.getReceipts(List.of("t1", "t2"));

        // Assert
        assertThat(receipts).containsOnlyKeys("t1");
        assertThat(receipts.get("t1").details().error()).isEqualTo("DeviceNotRegistered");
    }

    @Test
    void getReceipts_serverError_throwsBadGateway() {
        // Arrange
        server.expect(requestTo(BASE_URL + "/--/api/v2/push/getReceipts"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        // Act and assert
        assertThatThrownBy(() -> client.getReceipts(List.of("t1"))).isInstanceOf(BadGatewayException.class);
    }

    @Test
    void retryPolicy_bothCallsRetryUnderExpoPushAndOnlyAFiveHundredIsRetried() throws NoSuchMethodException {
        // Arrange
        Method send = ExpoPushClient.class.getMethod("send", List.class);
        Method receipts = ExpoPushClient.class.getMethod("getReceipts", List.class);

        // Assert
        assertThat(send.getAnnotation(ConnectRetry.class).value()).isEqualTo(ConnectRetryPolicy.EXPO_PUSH);
        assertThat(receipts.getAnnotation(ConnectRetry.class).value()).isEqualTo(ConnectRetryPolicy.EXPO_PUSH);
        assertThat(ConnectRetryPolicy.EXPO_PUSH.getMaxAttempts()).isEqualTo(3);
        assertThat(ConnectRetryPolicy.EXPO_PUSH.getRetryFor()).contains(Exception.class);
        assertThat(ConnectRetryPolicy.EXPO_PUSH.getNoRetryFor()).contains(InternalProcessException.class);
        assertThat(BadGatewayException.class).isNotIn((Object[]) ConnectRetryPolicy.EXPO_PUSH.getNoRetryFor());
    }
}
