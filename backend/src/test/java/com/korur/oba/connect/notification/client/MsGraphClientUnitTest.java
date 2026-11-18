package com.korur.oba.connect.notification.client;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2. Third-party
import com.microsoft.graph.models.User;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.UsersRequestBuilder;
import com.microsoft.graph.users.item.UserItemRequestBuilder;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

// 4. Internal
import com.korur.oba.connect.notification.dto.GraphMailbox;
import com.korur.oba.connect.notification.dto.GraphMailboxLookup;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class MsGraphClientUnitTest {

    private static final String MAIL_ADDRESS = "jane.doe@example.com";

    @Mock
    private GraphServiceClient graphServiceClient;

    @Mock
    private UsersRequestBuilder usersRequestBuilder;

    @Mock
    private UserItemRequestBuilder userItemRequestBuilder;

    @Mock
    private LoggingService loggingService;

    private MsGraphClient client;

    @BeforeEach
    void setUp() {
        client = new MsGraphClient(graphServiceClient, loggingService);
        when(graphServiceClient.users()).thenReturn(usersRequestBuilder);
    }

    private static ODataError graphError(int status) {
        ODataError error = mock(ODataError.class);
        when(error.getResponseStatusCode()).thenReturn(status);
        return error;
    }

    @Nested
    class FindMailbox {

        @BeforeEach
        void stubUserItem() {
            when(usersRequestBuilder.byUserId(MAIL_ADDRESS)).thenReturn(userItemRequestBuilder);
        }

        @Test
        void userWithMail_returnsMailboxAndSelectsOnlyMail() {
            // Arrange
            User graphUser = new User();
            graphUser.setMail(MAIL_ADDRESS);
            when(userItemRequestBuilder.get(any())).thenReturn(graphUser);

            // Act
            GraphMailboxLookup result = client.findMailbox(MAIL_ADDRESS);

            // Assert
            assertThat(result).isEqualTo(new GraphMailboxLookup.Found(new GraphMailbox(null, MAIL_ADDRESS)));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<UserItemRequestBuilder.GetRequestConfiguration>> captor =
                    ArgumentCaptor.forClass(Consumer.class);
            verify(userItemRequestBuilder).get(captor.capture());
            UserItemRequestBuilder.GetRequestConfiguration configuration =
                    userItemRequestBuilder.new GetRequestConfiguration();
            captor.getValue().accept(configuration);
            assertThat(configuration.queryParameters.select).containsExactly("mail");
        }

        @Test
        void userWithoutMail_returnsAccountWithoutMailbox() {
            // Arrange
            User graphUser = new User();
            graphUser.setDisplayName("No Mailbox");
            when(userItemRequestBuilder.get(any())).thenReturn(graphUser);

            // Act and assert
            assertThat(client.findMailbox(MAIL_ADDRESS)).isInstanceOf(GraphMailboxLookup.AccountWithoutMailbox.class);
        }

        @Test
        void graphAnswers404_returnsAccountNotFound() {
            // Arrange
            ODataError notFound = graphError(404);
            when(userItemRequestBuilder.get(any())).thenThrow(notFound);

            // Act and assert
            assertThat(client.findMailbox(MAIL_ADDRESS)).isInstanceOf(GraphMailboxLookup.AccountNotFound.class);
        }

        @Test
        void graphAnswersAnotherError_throwsUpstreamError() {
            // Arrange
            ODataError forbidden = graphError(403);
            when(userItemRequestBuilder.get(any())).thenThrow(forbidden);

            // Act and assert
            assertThatThrownBy(() -> client.findMailbox(MAIL_ADDRESS))
                    .isInstanceOfSatisfying(BadGatewayException.class, (BadGatewayException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION.getCode()));
        }

        @Test
        void graphUnreachable_throwsUpstreamError() {
            // Arrange
            when(userItemRequestBuilder.get(any())).thenThrow(new IllegalStateException("connection reset"));

            // Act and assert
            assertThatThrownBy(() -> client.findMailbox(MAIL_ADDRESS))
                    .isInstanceOfSatisfying(BadGatewayException.class, (BadGatewayException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION.getCode()));
        }
    }

    @Nested
    class FindSharedMailbox {

        private static final String SHARED_ADDRESS = "sales@example.com";

        private UserCollectionResponse collectionOf(List<User> users) {
            UserCollectionResponse response = new UserCollectionResponse();
            response.setValue(users);
            return response;
        }

        @SuppressWarnings("unchecked")
        private UsersRequestBuilder.GetRequestConfiguration capturedConfiguration() {
            ArgumentCaptor<Consumer<UsersRequestBuilder.GetRequestConfiguration>> captor =
                    ArgumentCaptor.forClass(Consumer.class);
            verify(usersRequestBuilder).get(captor.capture());
            UsersRequestBuilder.GetRequestConfiguration configuration =
                    usersRequestBuilder.new GetRequestConfiguration();
            captor.getValue().accept(configuration);
            return configuration;
        }

        @Test
        void memberWithMail_returnsMailbox() {
            // Arrange
            User graphUser = new User();
            graphUser.setMail(SHARED_ADDRESS);
            when(usersRequestBuilder.get(any())).thenReturn(collectionOf(List.of(graphUser)));

            // Act
            Optional<GraphMailbox> result = client.findSharedMailbox(SHARED_ADDRESS);

            // Assert
            assertThat(result).contains(new GraphMailbox(null, SHARED_ADDRESS));
        }

        @Test
        void memberWithIdAndMail_returnsMailboxWithTheGraphObjectId() {
            // Arrange
            String objectId = UUID.randomUUID().toString();
            User graphUser = new User();
            graphUser.setId(objectId);
            graphUser.setMail(SHARED_ADDRESS);
            when(usersRequestBuilder.get(any())).thenReturn(collectionOf(List.of(graphUser)));

            // Act
            Optional<GraphMailbox> result = client.findSharedMailbox(SHARED_ADDRESS);

            // Assert
            assertThat(result).contains(new GraphMailbox(objectId, SHARED_ADDRESS));
            assertThat(result.orElseThrow().id()).isEqualTo(objectId);
        }

        @Test
        void emptyCollection_returnsEmpty() {
            // Arrange
            when(usersRequestBuilder.get(any())).thenReturn(collectionOf(List.of()));

            // Act and assert
            assertThat(client.findSharedMailbox(SHARED_ADDRESS)).isEmpty();
        }

        @Test
        void addressWithQuote_filtersMembersByEscapedMailWithoutConsistencyLevel() {
            // Arrange
            when(usersRequestBuilder.get(any())).thenReturn(collectionOf(List.of()));

            // Act
            client.findSharedMailbox("o'brien@example.com");

            // Assert
            UsersRequestBuilder.GetRequestConfiguration configuration = capturedConfiguration();
            assertThat(configuration.queryParameters.filter)
                    .isEqualTo("mail eq 'o''brien@example.com' and userType eq 'Member'");
            assertThat(configuration.queryParameters.select).containsExactly("id", "mail");
            assertThat(configuration.queryParameters.top).isEqualTo(1);
            assertThat(configuration.headers.containsKey("ConsistencyLevel")).isFalse();
        }

        @Test
        void graphAnswersError_throwsUpstreamError() {
            // Arrange
            ODataError forbidden = graphError(403);
            when(usersRequestBuilder.get(any())).thenThrow(forbidden);

            // Act and assert
            assertThatThrownBy(() -> client.findSharedMailbox(SHARED_ADDRESS))
                    .isInstanceOfSatisfying(BadGatewayException.class, (BadGatewayException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION.getCode()));
        }

        @Test
        void graphUnreachable_throwsUpstreamError() {
            // Arrange
            when(usersRequestBuilder.get(any())).thenThrow(new IllegalStateException("connection reset"));

            // Act and assert
            assertThatThrownBy(() -> client.findSharedMailbox(SHARED_ADDRESS))
                    .isInstanceOfSatisfying(BadGatewayException.class, (BadGatewayException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION.getCode()));
        }
    }
}
