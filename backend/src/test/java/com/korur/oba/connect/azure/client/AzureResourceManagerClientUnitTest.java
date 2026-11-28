package com.korur.oba.connect.azure.client;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 2. Third-party

// 3. Java SDK
import java.io.IOException;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.mapper.AzureResourceManagerPayloadMapperImpl;
import com.korur.oba.connect.azure.mock_data.AzureConnectionMocks;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureResourceManagerClientUnitTest {

    private static final String NEXT_URL =
            "https://management.azure.com/subscriptions?api-version=2022-12-01&%24skiptoken=page2";

    private final AzureConnectionTokenProvider tokenProvider = mock(AzureConnectionTokenProvider.class);
    private MockRestServiceServer server;
    private AzureResourceManagerClient client;
    private AzureConnection connection;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = AzureConnectionMocks.restClientBuilder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AzureResourceManagerClient(builder.build(), tokenProvider,
                new AzureResourceManagerPayloadMapperImpl(), mock(LoggingService.class));
        connection = AzureConnectionMocks.connection();
        when(tokenProvider.getAccessToken(any())).thenReturn(AzureConnectionMocks.TOKEN);
    }

    private void expectPage(String url, String body) {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + AzureConnectionMocks.TOKEN))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private ApplicationException listFails() {
        return assertThrows(ApplicationException.class, () -> client.listSubscriptions(connection));
    }

    @Test
    void listSubscriptions_twoPages_followsTheNextLinkAndMapsEveryItem() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        expectPage(AzureConnectionMocks.SUBSCRIPTIONS_URL, AzureConnectionMocks.subscriptionPage(NEXT_URL,
                AzureConnectionMocks.subscription(first, "Production", "Enabled")));
        expectPage(NEXT_URL, AzureConnectionMocks.subscriptionPage(null,
                AzureConnectionMocks.subscription(second, "Staging", "Warned")));

        List<AzureSubscriptionPayload> result = client.listSubscriptions(connection);

        assertThat(result).containsExactly(
                new AzureSubscriptionPayload(first, "Production", "Enabled"),
                new AzureSubscriptionPayload(second, "Staging", "Warned"));
        server.verify();
    }

    @Test
    void listSubscriptions_none_isEmpty() {
        expectPage(AzureConnectionMocks.SUBSCRIPTIONS_URL, AzureConnectionMocks.subscriptionPage(null));

        assertThat(client.listSubscriptions(connection)).isEmpty();
    }

    @Test
    void listSubscriptions_itemWithoutState_isLeftOut() {
        UUID kept = UUID.randomUUID();
        expectPage(AzureConnectionMocks.SUBSCRIPTIONS_URL, AzureConnectionMocks.subscriptionPage(null,
                "{\"subscriptionId\":\"" + UUID.randomUUID() + "\",\"displayName\":\"No state\"}",
                AzureConnectionMocks.subscription(kept, "Production", "Enabled")));

        assertThat(client.listSubscriptions(connection))
                .containsExactly(new AzureSubscriptionPayload(kept, "Production", "Enabled"));
    }

    @Test
    void listSubscriptions_nextLinkToAnotherHost_isRefusedWithoutSendingTheToken() {
        expectPage(AzureConnectionMocks.SUBSCRIPTIONS_URL,
                AzureConnectionMocks.subscriptionPage("https://evil.example/subscriptions"));

        ApplicationException exception = listFails();

        assertThat(exception.getErrorCode())
                .isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_UNEXPECTED_RESPONSE.getCode());
        server.verify();
    }

    @Test
    void listSubscriptions_forbidden_isCredentialsRefused() {
        server.expect(requestTo(AzureConnectionMocks.SUBSCRIPTIONS_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"AuthorizationFailed\",\"message\":\"secret detail\"}}"));

        assertThat(listFails().getErrorCode())
                .isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_CREDENTIALS_REFUSED.getCode());
    }

    @Test
    void listSubscriptions_tooManyRequests_isRateLimited() {
        server.expect(requestTo(AzureConnectionMocks.SUBSCRIPTIONS_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(listFails().getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_RATE_LIMITED.getCode());
    }

    @Test
    void listSubscriptions_notFound_isUnexpectedResponse() {
        server.expect(requestTo(AzureConnectionMocks.SUBSCRIPTIONS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(listFails().getErrorCode())
                .isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_UNEXPECTED_RESPONSE.getCode());
    }

    @Test
    void listSubscriptions_unreadableBody_isUnexpectedResponse() {
        expectPage(AzureConnectionMocks.SUBSCRIPTIONS_URL, "{\"value\":[{\"subscriptionId\":\"not-a-uuid\"}]}");

        assertThat(listFails().getErrorCode())
                .isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_UNEXPECTED_RESPONSE.getCode());
    }

    @Test
    void listSubscriptions_noAnswer_isUnreachable() {
        server.expect(requestTo(AzureConnectionMocks.SUBSCRIPTIONS_URL)).andRespond(request -> {
            throw new IOException("Connection reset");
        });

        assertThat(listFails().getErrorCode()).isEqualTo(ExceptionDomainAzureError.UPS_AZURE_ARM_UNREACHABLE.getCode());
    }
}
