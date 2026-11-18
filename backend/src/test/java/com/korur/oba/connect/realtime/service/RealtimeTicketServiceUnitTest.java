package com.korur.oba.connect.realtime.service;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockingDetails;

// 2. Third-party

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.dto.RealtimeTicketResponse;
import com.korur.oba.connect.realtime.exception.ExceptionDomainRealtimeError;
import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.TooManyRequestsException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;

@ExtendWith(MockitoExtension.class)
class RealtimeTicketServiceUnitTest {

    private static final Instant START = Instant.parse("2026-10-26T10:00:00Z");

    @Mock
    private LoggingService loggingService;
    @Mock
    private WebSocketHandler webSocketHandler;

    private final MutableClock clock = new MutableClock(START);
    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final User user = User.builder().id(userId).build();
    private final Instant tokenExpiresAt = START.plusSeconds(300);
    private RealtimeTicketService service;

    @BeforeEach
    void setUp() {
        service = new RealtimeTicketService(new InMemoryRealtimeTicketStore(clock), loggingService, clock);
        authenticate(tokenExpiresAt);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Instant expiresAt) {
        Jwt.Builder builder = Jwt.withTokenValue("access-token").header("alg", "none").subject("keycloak-subject");
        if (expiresAt != null) {
            builder.issuedAt(expiresAt.minusSeconds(600)).expiresAt(expiresAt);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    /**
     * Runs a handshake with the given query string, which may be null.
     */
    private HandshakeResult handshake(String queryString) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", RealtimeConstant.ENDPOINT_PATH);
        servletRequest.setQueryString(queryString);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = service.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse), webSocketHandler, attributes);
        return new HandshakeResult(accepted, servletResponse.getStatus(), attributes);
    }

    private HandshakeResult handshakeWith(String ticket) {
        return handshake(RealtimeConstant.TICKET_QUERY_PARAMETER + "=" + ticket);
    }

    private record HandshakeResult(boolean accepted, int status, Map<String, Object> attributes) {

        void assertRefused() {
            assertThat(accepted).isFalse();
            assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(attributes).doesNotContainKey(RealtimeConstant.SESSION_PRINCIPAL_ATTRIBUTE);
        }
    }

    @Nested
    class IssueTicket {

        @Test
        void validRequest_returnsA256BitTicketThatLives30Seconds() {
            // Act
            RealtimeTicketResponse response = service.issueTicket(user, organizationId);

            // Assert
            assertThat(response.ticket()).matches("[A-Za-z0-9_-]{43}");
            assertThat(response.expiresAt()).isEqualTo(START.plusSeconds(30));
        }

        @Test
        void twoRequests_returnDifferentTickets() {
            assertThat(service.issueTicket(user, organizationId).ticket())
                    .isNotEqualTo(service.issueTicket(user, organizationId).ticket());
        }

        @Test
        void tokenWithoutExpiry_throwsForbiddenRealtimeError() {
            // Arrange
            authenticate(null);

            // Act and Assert
            assertThatThrownBy(() -> service.issueTicket(user, organizationId))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainRealtimeError.SEC_REALTIME_FORBIDDEN.getCode());
        }

        @Test
        void sixthLiveTicket_throwsTooManyRequests() {
            // Arrange
            for (int index = 0; index < RealtimeConstant.MAX_LIVE_TICKETS_PER_USER; index++) {
                service.issueTicket(user, organizationId);
            }

            // Act and Assert
            assertThatThrownBy(() -> service.issueTicket(user, organizationId))
                    .isInstanceOf(TooManyRequestsException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionApiError.TOO_MANY_REQUESTS.getCode());
        }

        @Test
        void sixthTicketAfterTheEarlierOnesExpired_isIssued() {
            // Arrange
            for (int index = 0; index < RealtimeConstant.MAX_LIVE_TICKETS_PER_USER; index++) {
                service.issueTicket(user, organizationId);
            }
            clock.advanceSeconds(30);

            // Act and Assert
            assertThat(service.issueTicket(user, organizationId).ticket()).isNotBlank();
        }

        @Test
        void ticketsOfAnotherUser_doNotCountTowardsTheLimit() {
            // Arrange
            User otherUser = User.builder().id(UUID.randomUUID()).build();
            for (int index = 0; index < RealtimeConstant.MAX_LIVE_TICKETS_PER_USER; index++) {
                service.issueTicket(otherUser, organizationId);
            }

            // Act and Assert
            assertThat(service.issueTicket(user, organizationId).ticket()).isNotBlank();
        }
    }

    @Nested
    class BeforeHandshake {

        @Test
        void validTicket_acceptsAndPutsThePrincipalIntoTheAttributes() {
            // Arrange
            String ticket = service.issueTicket(user, organizationId).ticket();

            // Act
            HandshakeResult result = handshakeWith(ticket);

            // Assert
            assertThat(result.accepted()).isTrue();
            assertThat(result.attributes()).containsEntry(RealtimeConstant.SESSION_PRINCIPAL_ATTRIBUTE,
                    new RealtimePrincipal(userId, organizationId, tokenExpiresAt));
        }

        @Test
        void ticketUsedTwice_secondHandshakeIsRefused() {
            // Arrange
            String ticket = service.issueTicket(user, organizationId).ticket();
            handshakeWith(ticket);

            // Act and Assert
            handshakeWith(ticket).assertRefused();
        }

        @Test
        void ticketPastItsLifetime_isRefused() {
            // Arrange
            String ticket = service.issueTicket(user, organizationId).ticket();
            clock.advanceSeconds(30);

            // Act and Assert
            handshakeWith(ticket).assertRefused();
        }

        @Test
        void ticketJustBeforeItsExpiry_isAccepted() {
            // Arrange
            String ticket = service.issueTicket(user, organizationId).ticket();
            clock.advanceSeconds(29);

            // Act and Assert
            assertThat(handshakeWith(ticket).accepted()).isTrue();
        }

        @Test
        void unknownTicket_isRefused() {
            handshakeWith("never-issued").assertRefused();
        }

        @Test
        void missingTicket_isRefused() {
            handshake(null).assertRefused();
        }

        @Test
        void blankTicket_isRefused() {
            handshake(RealtimeConstant.TICKET_QUERY_PARAMETER + "=").assertRefused();
        }
    }

    @Nested
    class OnAccessRevoked {

        @Test
        void memberEvent_dropsThatMembershipsTicketsOnly() {
            // Arrange
            String revoked = service.issueTicket(user, organizationId).ticket();
            String otherOrganization = service.issueTicket(user, UUID.randomUUID()).ticket();

            // Act
            service.onAccessRevoked(AccessRevokedEvent.member(userId, organizationId));

            // Assert
            handshakeWith(revoked).assertRefused();
            assertThat(handshakeWith(otherOrganization).accepted()).isTrue();
        }

        @Test
        void kazanLost_dropsThatMembershipsTicketsOnly() {
            // Arrange
            String revoked = service.issueTicket(user, organizationId).ticket();
            String otherOrganization = service.issueTicket(user, UUID.randomUUID()).ticket();

            // Act
            service.onAppAccessRevoked(new AppAccessRevokedEvent(userId, organizationId, App.KAZAN));

            // Assert
            handshakeWith(revoked).assertRefused();
            assertThat(handshakeWith(otherOrganization).accepted()).isTrue();
        }

        @Test
        void appWithoutRealtimeLost_keepsTheTickets() {
            // Arrange
            String kept = service.issueTicket(user, organizationId).ticket();

            // Act
            service.onAppAccessRevoked(new AppAccessRevokedEvent(userId, organizationId, App.KAYA));

            // Assert
            assertThat(handshakeWith(kept).accepted()).isTrue();
        }

        @Test
        void userEvent_dropsTheUsersTicketsInEveryOrganization() {
            // Arrange
            String first = service.issueTicket(user, organizationId).ticket();
            String second = service.issueTicket(user, UUID.randomUUID()).ticket();

            // Act
            service.onAccessRevoked(AccessRevokedEvent.user(userId));

            // Assert
            handshakeWith(first).assertRefused();
            handshakeWith(second).assertRefused();
        }

        @Test
        void organizationEvent_dropsEveryMembersTicketOfThatOrganization() {
            // Arrange
            String own = service.issueTicket(user, organizationId).ticket();
            String otherMember = service.issueTicket(User.builder().id(UUID.randomUUID()).build(), organizationId).ticket();

            // Act
            service.onAccessRevoked(AccessRevokedEvent.organization(organizationId));

            // Assert
            handshakeWith(own).assertRefused();
            handshakeWith(otherMember).assertRefused();
        }
    }

    @Test
    void issueAndRedeem_neverLogTheTicket() {
        // Arrange and Act
        String redeemed = service.issueTicket(user, organizationId).ticket();
        handshakeWith(redeemed);
        handshakeWith(redeemed);

        // Assert
        assertThat(mockingDetails(loggingService).getInvocations()).isNotEmpty();
        mockingDetails(loggingService).getInvocations().forEach(invocation ->
                Arrays.asList(invocation.getRawArguments()).forEach((Object argument) -> {
                    Object[] values = argument instanceof Object[] varargs ? varargs : new Object[] {argument};
                    for (Object value : values) {
                        assertThat(String.valueOf(value)).doesNotContain(redeemed);
                    }
                }));
    }

    /**
     * A clock the test moves forward by hand.
     */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
