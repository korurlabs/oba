package com.korur.oba.connect.azure.client;

// 1. Framework-level
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

// 2. Third-party

// 3. Java SDK
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureResourceManagerConstant;
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.dto.api.AzureApiSubscription;
import com.korur.oba.connect.azure.dto.api.AzureApiSubscriptionList;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.helper.AzureResourceManagerErrorTranslator;
import com.korur.oba.connect.azure.mapper.AzureResourceManagerPayloadMapper;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.azure.constant.client.AzureResourceManagerClientLogConstant.*;

/**
 * Reads Azure Resource Manager as the identity of one connection. GET only: it changes nothing in
 * the organization's Azure. It decides no access; the caller loads the connection by its id and
 * organization first, so a request only ever carries the token of the connection it names.
 *
 * Calls are synchronous, with the connect and read timeouts of the client this class is given,
 * and are not retried: the next check or poll tries again. A 404 is an empty result; every other
 * failure is logged with its status and thrown as an AZR error (see
 * {@link AzureResourceManagerErrorTranslator}). The Authorization header, the token and the body of
 * an error answer are never logged.
 */
public class AzureResourceManagerClient {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureResourceManagerClient.class);

    private final RestClient restClient;
    private final AzureConnectionTokenProvider tokenProvider;
    private final AzureResourceManagerPayloadMapper payloadMapper;
    private final LoggingService loggingService;

    /**
     * Creates the client.
     *
     * @param restClient     a client with Resource Manager's timeouts
     * @param tokenProvider  the source of tokens per connection
     * @param payloadMapper  maps Resource Manager's payloads
     * @param loggingService the logging service
     */
    public AzureResourceManagerClient(RestClient restClient, AzureConnectionTokenProvider tokenProvider,
                                      AzureResourceManagerPayloadMapper payloadMapper,
                                      LoggingService loggingService) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
        this.payloadMapper = payloadMapper;
        this.loggingService = loggingService;
    }

    /**
     * Lists every subscription the identity of the connection can read, following the next page
     * links within Resource Manager. A subscription Azure lists without its id, name or state is
     * left out with a warn line.
     *
     * @param connection the connection, loaded by the caller; it must have a client id
     * @return the subscriptions, empty when the identity can read none
     */
    public List<AzureSubscriptionPayload> listSubscriptions(AzureConnection connection) {
        List<AzureSubscriptionPayload> subscriptions = new ArrayList<>();
        URI pageUri = UriComponentsBuilder.fromUriString(AzureResourceManagerConstant.BASE_URL)
                .path(AzureResourceManagerConstant.SUBSCRIPTIONS_PATH)
                .queryParam(AzureResourceManagerConstant.PARAM_API_VERSION,
                        AzureResourceManagerConstant.SUBSCRIPTIONS_API_VERSION)
                .build()
                .toUri();
        int pages = 0;
        while (pageUri != null) {
            if (pages == AzureResourceManagerConstant.MAX_PAGES) {
                loggingService.error(LOG_PREFIX, LogLayer.CLIENT, PAGE_LIMIT_REACHED,
                        AzureResourceManagerConstant.MAX_PAGES, OPERATION_LIST_SUBSCRIPTIONS, connection.getId());
                throw AzureResourceManagerErrorTranslator.unexpectedResponse();
            }
            Optional<AzureApiSubscriptionList> page =
                    get(OPERATION_LIST_SUBSCRIPTIONS, connection, pageUri, AzureApiSubscriptionList.class);
            if (page.isEmpty()) {
                // The subscription list always exists, so a 404 means the answer is not what it seems.
                loggingService.error(LOG_PREFIX, LogLayer.CLIENT, SUBSCRIPTIONS_ABSENT, connection.getId());
                throw AzureResourceManagerErrorTranslator.unexpectedResponse();
            }
            addCompleteSubscriptions(connection, page.get().value(), subscriptions);
            pageUri = nextPage(OPERATION_LIST_SUBSCRIPTIONS, connection, page.get().nextLink());
            pages++;
        }
        loggingService.info(LOG_PREFIX, LogLayer.CLIENT, SUBSCRIPTIONS_LOADED, subscriptions.size(), connection.getId());
        return List.copyOf(subscriptions);
    }

    private void addCompleteSubscriptions(AzureConnection connection, List<AzureApiSubscription> pageItems,
                                          List<AzureSubscriptionPayload> subscriptions) {
        if (pageItems == null) {
            return;
        }
        for (AzureApiSubscription item : pageItems) {
            if (item == null || item.subscriptionId() == null || item.displayName() == null || item.state() == null) {
                loggingService.warn(LOG_PREFIX, LogLayer.CLIENT, SUBSCRIPTION_INCOMPLETE, connection.getId());
                continue;
            }
            subscriptions.add(payloadMapper.toSubscriptionPayload(item));
        }
    }

    /**
     * Turns a next page link into the URI to read, or null on the last page. A link to any other
     * scheme or host is refused, so the connection's token is never sent outside Resource Manager.
     */
    private URI nextPage(String operation, AzureConnection connection, String nextLink) {
        if (nextLink == null || nextLink.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(nextLink);
        } catch (IllegalArgumentException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, NEXT_LINK_REFUSED, operation, connection.getId());
            throw AzureResourceManagerErrorTranslator.unexpectedResponse();
        }
        if (!AzureResourceManagerConstant.BASE_SCHEME.equalsIgnoreCase(uri.getScheme())
                || !AzureResourceManagerConstant.BASE_HOST.equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, NEXT_LINK_REFUSED, operation, connection.getId());
            throw AzureResourceManagerErrorTranslator.unexpectedResponse();
        }
        return uri;
    }

    /**
     * Runs one GET as the connection's identity.
     *
     * The exception of an error answer is not attached to the log line, because its message holds
     * the answer's body, which can name the identity and the organization's resources. A body that
     * cannot be read is logged with the type of its cause only, for the same reason.
     */
    private <T> Optional<T> get(String operation, AzureConnection connection, URI uri, Class<T> type) {
        String token = tokenProvider.getAccessToken(connection);
        try {
            T body = restClient.get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(type);
            if (body == null) {
                loggingService.error(LOG_PREFIX, LogLayer.CLIENT, CALL_RESPONSE_UNREADABLE, operation,
                        connection.getId(), BODY_ABSENT);
                throw AzureResourceManagerErrorTranslator.unexpectedResponse();
            }
            return Optional.of(body);
        } catch (HttpClientErrorException.NotFound exception) {
            // An absent resource is an answer, not a failure: the caller decides what it means.
            loggingService.info(LOG_PREFIX, LogLayer.CLIENT, RESOURCE_ABSENT, operation, connection.getId());
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, CALL_FAILED_STATUS, operation, connection.getId(),
                    exception.getStatusCode().value());
            throw AzureResourceManagerErrorTranslator.fromResponse(exception);
        } catch (RestClientException exception) {
            if (isUnreadableBody(exception)) {
                loggingService.error(LOG_PREFIX, LogLayer.CLIENT, CALL_RESPONSE_UNREADABLE, operation,
                        connection.getId(), exception.getCause().getClass().getName());
                throw AzureResourceManagerErrorTranslator.unexpectedResponse();
            }
            loggingService.error(LOG_PREFIX, LogLayer.CLIENT, CALL_FAILED_IO, exception, operation, connection.getId());
            throw AzureResourceManagerErrorTranslator.unreachable();
        }
    }

    private boolean isUnreadableBody(RestClientException exception) {
        return exception.getCause() instanceof HttpMessageNotReadableException;
    }
}
