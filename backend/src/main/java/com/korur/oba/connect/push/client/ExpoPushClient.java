package com.korur.oba.connect.push.client;

// 1. Framework-level
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.dto.expo.ExpoPushMessage;
import com.korur.oba.connect.push.dto.expo.ExpoPushReceipt;
import com.korur.oba.connect.push.dto.expo.ExpoPushReceiptResponse;
import com.korur.oba.connect.push.dto.expo.ExpoPushSendResponse;
import com.korur.oba.connect.push.dto.expo.ExpoPushTicket;
import com.korur.oba.connect.push.exception.ExceptionDomainPushError;
import com.korur.oba.connect.push.properties.ExpoProperties;
import com.korur.oba.connect.retry.annotation.ConnectRetry;
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;
import com.korur.oba.dojo.exception.external.InternalProcessException;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.push.constant.client.ExpoPushClientLogConstant.*;

/**
 * Calls the Expo Push Service: sends messages and reads the receipts of their tickets. It decides no
 * recipient and holds no state; the caller checks {@link #isEnabled} first.
 *
 * Both calls retry under {@link ConnectRetryPolicy#EXPO_PUSH}: a 5xx, a 429 or a network failure is
 * retried, and an exhausted call is recorded as a retry dead event. A 4xx other than 429 means a wrong
 * access token or a malformed request, which a retry cannot fix, so it fails at once.
 *
 * The exceptions thrown never carry Expo's response body or the underlying exception, because Expo
 * quotes push tokens in its error bodies and the retry module stores the error message.
 */
public class ExpoPushClient {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ExpoPushClient.class);

    private final RestClient restClient;
    private final ExpoProperties properties;
    private final LoggingService loggingService;

    /**
     * Creates the client.
     *
     * @param restClient     a client with the Expo base URL and its timeouts
     * @param properties     the Expo configuration
     * @param loggingService the logging service
     */
    public ExpoPushClient(RestClient restClient, ExpoProperties properties, LoggingService loggingService) {
        this.restClient = restClient;
        this.properties = properties;
        this.loggingService = loggingService;
    }

    /**
     * Tells whether push sending is on. Every other method may be called only when this is true.
     *
     * @return true when the Expo access token is set
     */
    public boolean isEnabled() {
        return properties.isConfigured();
    }

    /**
     * Sends messages to Expo.
     *
     * @param messages at most 100 messages
     * @return one ticket per message, in the order of the messages
     */
    @ConnectRetry(ConnectRetryPolicy.EXPO_PUSH)
    public List<ExpoPushTicket> send(List<ExpoPushMessage> messages) {
        ExpoPushSendResponse response = execute(PushConstant.OPERATION_SEND, () -> restClient.post()
                .uri(PushConstant.EXPO_PATH_SEND)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(messages)
                .retrieve()
                .body(ExpoPushSendResponse.class));
        List<ExpoPushTicket> tickets = response == null || response.data() == null ? List.of() : response.data();
        loggingService.debug(LOG_PREFIX, LogLayer.CLIENT, REQUEST_SENT, PushConstant.OPERATION_SEND, tickets.size());
        return tickets;
    }

    /**
     * Reads the receipts of tickets.
     *
     * @param ticketIds at most 1000 ticket ids
     * @return the receipts by ticket id; a ticket without a receipt yet is absent
     */
    @ConnectRetry(ConnectRetryPolicy.EXPO_PUSH)
    public Map<String, ExpoPushReceipt> getReceipts(List<String> ticketIds) {
        ExpoPushReceiptResponse response = execute(PushConstant.OPERATION_GET_RECEIPTS, () -> restClient.post()
                .uri(PushConstant.EXPO_PATH_RECEIPTS)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(PushConstant.EXPO_FIELD_IDS, ticketIds))
                .retrieve()
                .body(ExpoPushReceiptResponse.class));
        Map<String, ExpoPushReceipt> receipts = response == null || response.data() == null
                ? Map.of()
                : response.data();
        loggingService.debug(LOG_PREFIX, LogLayer.CLIENT, REQUEST_SENT, PushConstant.OPERATION_GET_RECEIPTS,
                receipts.size());
        return receipts;
    }

    private void authorize(HttpHeaders headers) {
        headers.setBearerAuth(properties.getAccessToken());
    }

    /**
     * Runs one call and turns its failure into an exception the retry policy classifies: a
     * {@link BadGatewayException} is retried, an {@link InternalProcessException} is not.
     */
    private <T> T execute(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (exception.getStatusCode().is4xxClientError() && status != PushConstant.HTTP_STATUS_TOO_MANY_REQUESTS) {
                loggingService.error(LOG_PREFIX, LogLayer.CLIENT, REQUEST_REJECTED, operation, status);
                throw new InternalProcessException(ExceptionDomainPushError.INT_EXPO_REQUEST_REJECTED, null);
            }
            loggingService.warn(LOG_PREFIX, LogLayer.CLIENT, REQUEST_FAILED, operation, status);
            throw new BadGatewayException(ExceptionDomainPushError.UPS_EXPO_UNAVAILABLE,
                    Map.of(PushConstant.FIELD_OPERATION, operation, PushConstant.FIELD_STATUS, status));
        } catch (ResourceAccessException exception) {
            // An I/O failure names only the URL and the cause, never a token.
            loggingService.warn(LOG_PREFIX, LogLayer.CLIENT, REQUEST_UNREACHABLE, operation, exception.getMessage());
            throw new BadGatewayException(ExceptionDomainPushError.UPS_EXPO_UNAVAILABLE,
                    Map.of(PushConstant.FIELD_OPERATION, operation));
        } catch (RestClientException exception) {
            // A body that cannot be read is not retried; only the exception type is logged, since its
            // message may quote the body.
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, RESPONSE_UNREADABLE, operation,
                    exception.getClass().getSimpleName());
            throw new InternalProcessException(ExceptionDomainPushError.INT_EXPO_REQUEST_REJECTED, null);
        }
    }
}
