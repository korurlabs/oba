package com.korur.oba.connect.realtime.helper.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.dto.RealtimeFrameDecision;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.helper.RealtimeDestinationAuthorizerRegistry;
import com.korur.oba.connect.realtime.service.RealtimeDestinationAuthorizer;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.dojo.logging.service.LoggingService;

class RealtimeDestinationAuthorizerRegistryUnitTest {

    private static final String SUFFIX = "/things";
    private static final String USER_QUEUE = "/user/queue/things";
    private static final String SEND_DESTINATION = "/app/things/poke";

    private final UUID organizationId = UUID.randomUUID();
    private final RealtimePrincipal principal =
            new RealtimePrincipal(UUID.randomUUID(), organizationId, Instant.now().plusSeconds(300));

    private String ownTopic() {
        return RealtimeDestinationUtil.organizationTopic(organizationId, SUFFIX);
    }

    private static RealtimeDestinationAuthorizerRegistry registry(RealtimeDestinationAuthorizer... authorizers) {
        return new RealtimeDestinationAuthorizerRegistry(List.of(authorizers), new LoggingService());
    }

    private static void assertVerdict(RealtimeFrameDecision decision, RealtimeFrameDecision.Verdict verdict, String reason) {
        assertThat(decision.verdict()).isEqualTo(verdict);
        assertThat(decision.reason()).isEqualTo(reason);
    }

    @Test
    void constructor_twoAuthorizersWithOneConsumerName_failsStartup() {
        assertThatThrownBy(() -> registry(new StubAuthorizer("things", Boolean.TRUE),
                new StubAuthorizer("things", Boolean.TRUE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("things");
    }

    @Nested
    class Subscribe {

        private final RealtimeDestinationAuthorizerRegistry allowing =
                registry(new StubAuthorizer("things", Boolean.TRUE));

        @Test
        void claimedOwnOrganizationTopic_passes() {
            assertThat(allowing.authorizeSubscribe(principal, ownTopic()).passes()).isTrue();
        }

        @Test
        void claimedUserQueue_passes() {
            assertThat(allowing.authorizeSubscribe(principal, USER_QUEUE).passes()).isTrue();
        }

        @Test
        void unclaimedDestination_isRefused() {
            assertVerdict(allowing.authorizeSubscribe(principal,
                            RealtimeDestinationUtil.organizationTopic(organizationId, "/other")),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_UNCLAIMED);
        }

        @Test
        void noAuthorizerAtAll_refusesEverything() {
            assertVerdict(registry().authorizeSubscribe(principal, ownTopic()),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_UNCLAIMED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"/topic/organizations/*/things", "/topic/organizations/**", "/topic/thing?",
                "/topic/{organizationId}/things", "/topic/organizations/../things", "/user/queue/*"})
        void wildcardOrDotDot_isRefused(String destination) {
            assertVerdict(allowing.authorizeSubscribe(principal, destination),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_PATTERN);
        }

        @Test
        void otherOrganizationTopic_isRefused() {
            assertVerdict(allowing.authorizeSubscribe(principal,
                            RealtimeDestinationUtil.organizationTopic(UUID.randomUUID(), SUFFIX)),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_ORGANIZATION);
        }

        @Test
        void nonCanonicalOwnOrganizationId_isRefused() {
            String upperCase = RealtimeConstant.ORGANIZATION_TOPIC_PREFIX
                    + organizationId.toString().toUpperCase(Locale.ROOT) + SUFFIX;

            assertVerdict(allowing.authorizeSubscribe(principal, upperCase),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_ORGANIZATION);
        }

        @Test
        void organizationSegmentThatIsNoId_isRefused() {
            assertVerdict(allowing.authorizeSubscribe(principal, "/topic/organizations/all/things"),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_ORGANIZATION);
        }

        @ParameterizedTest
        @ValueSource(strings = {"/topic/things", "/topic/organizations", "/topic/organizations/",
                "/topic/broadcast/things"})
        void topicNotScopedToAnOrganization_isRefusedEvenWhenClaimed(String destination) {
            RealtimeDestinationAuthorizerRegistry claimingEverything = registry(new ClaimAllAuthorizer());

            assertVerdict(claimingEverything.authorizeSubscribe(principal, destination),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_ORGANIZATION);
        }

        @ParameterizedTest
        @ValueSource(strings = {"/queue/things", "/queue/things-user1", "/user/someone/queue/things",
                "/app/things/poke", "/topicthings"})
        void outsideTopicAndUserQueue_isRefused(String destination) {
            assertVerdict(allowing.authorizeSubscribe(principal, destination),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_PREFIX);
        }

        @Test
        void blankDestination_isRefused() {
            assertVerdict(allowing.authorizeSubscribe(principal, " "),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_BLANK);
            assertVerdict(allowing.authorizeSubscribe(principal, null),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_BLANK);
        }

        @Test
        void tooLongDestination_isRefused() {
            String tooLong = "/topic/" + "a".repeat(RealtimeConstant.MAX_DESTINATION_LENGTH);

            assertVerdict(allowing.authorizeSubscribe(principal, tooLong),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_TOO_LONG);
        }

        @Test
        void twoClaimants_isRefused() {
            RealtimeDestinationAuthorizerRegistry contested = registry(
                    new StubAuthorizer("things", Boolean.TRUE),
                    new StubAuthorizer("more-things", Boolean.TRUE));

            assertVerdict(contested.authorizeSubscribe(principal, ownTopic()),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_CONTESTED);
        }

        @Test
        void claimantDenies_isDropped() {
            RealtimeDestinationAuthorizerRegistry denying =
                    registry(new StubAuthorizer("things", Boolean.FALSE));

            assertVerdict(denying.authorizeSubscribe(principal, ownTopic()),
                    RealtimeFrameDecision.Verdict.DROP, RealtimeConstant.REASON_CONSUMER_DENIED);
        }

        @Test
        void claimantThrows_isDropped() {
            RealtimeDestinationAuthorizerRegistry failing = registry(new StubAuthorizer("things", null));

            assertVerdict(failing.authorizeSubscribe(principal, ownTopic()),
                    RealtimeFrameDecision.Verdict.DROP, RealtimeConstant.REASON_CONSUMER_FAILED);
        }
    }

    @Nested
    class Send {

        private final RealtimeDestinationAuthorizerRegistry allowing =
                registry(new StubAuthorizer("things", Boolean.TRUE));

        @Test
        void claimedApplicationDestination_passes() {
            assertThat(allowing.authorizeSend(principal, SEND_DESTINATION).passes()).isTrue();
        }

        @Test
        void unclaimedApplicationDestination_isRefused() {
            assertVerdict(allowing.authorizeSend(principal, "/app/things/other"),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_UNCLAIMED);
        }

        @Test
        void toATopic_isRefused() {
            assertVerdict(allowing.authorizeSend(principal, ownTopic()),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_PREFIX);
        }

        @Test
        void toAUserDestination_isRefused() {
            assertVerdict(allowing.authorizeSend(principal, USER_QUEUE),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_PREFIX);
        }

        @Test
        void wildcard_isRefused() {
            assertVerdict(allowing.authorizeSend(principal, "/app/things/*"),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_PATTERN);
        }

        @Test
        void consumerWithoutSendSupport_refusesItsOwnSubscribeDestinationsForSend() {
            RealtimeDestinationAuthorizerRegistry subscribeOnly = registry(new SubscribeOnlyAuthorizer());

            assertVerdict(subscribeOnly.authorizeSend(principal, SEND_DESTINATION),
                    RealtimeFrameDecision.Verdict.REFUSE, RealtimeConstant.REASON_DESTINATION_UNCLAIMED);
        }

        @Test
        void claimantDenies_isDropped() {
            RealtimeDestinationAuthorizerRegistry denying =
                    registry(new StubAuthorizer("things", Boolean.FALSE));

            assertVerdict(denying.authorizeSend(principal, SEND_DESTINATION),
                    RealtimeFrameDecision.Verdict.DROP, RealtimeConstant.REASON_CONSUMER_DENIED);
        }
    }

    /**
     * Claims the "things" topic of any organization, its user queue and one send destination,
     * and answers with a fixed decision; a null decision makes it throw.
     */
    private static final class StubAuthorizer implements RealtimeDestinationAuthorizer {

        private final String consumer;
        private final Boolean decision;

        private StubAuthorizer(String consumer, Boolean decision) {
            this.consumer = consumer;
            this.decision = decision;
        }

        @Override
        public String consumer() {
            return consumer;
        }

        @Override
        public boolean supportsSubscribe(String destination) {
            return USER_QUEUE.equals(destination)
                    || RealtimeDestinationUtil.parseOrganizationTopic(destination, SUFFIX).isPresent();
        }

        @Override
        public boolean authorizeSubscribe(RealtimePrincipal principal, String destination) {
            return answer();
        }

        @Override
        public boolean supportsSend(String destination) {
            return SEND_DESTINATION.equals(destination);
        }

        @Override
        public boolean authorizeSend(RealtimePrincipal principal, String destination) {
            return answer();
        }

        private boolean answer() {
            if (decision == null) {
                throw new IllegalStateException("board lookup failed");
            }
            return decision;
        }
    }

    /**
     * A consumer that takes no client frames and keeps the default send methods.
     */
    private static final class SubscribeOnlyAuthorizer implements RealtimeDestinationAuthorizer {

        @Override
        public String consumer() {
            return "subscribe-only";
        }

        @Override
        public boolean supportsSubscribe(String destination) {
            return USER_QUEUE.equals(destination);
        }

        @Override
        public boolean authorizeSubscribe(RealtimePrincipal principal, String destination) {
            return true;
        }
    }

    /**
     * A consumer that claims and allows every destination, so only the shared rules can refuse.
     */
    private static final class ClaimAllAuthorizer implements RealtimeDestinationAuthorizer {

        @Override
        public String consumer() {
            return "claim-all";
        }

        @Override
        public boolean supportsSubscribe(String destination) {
            return true;
        }

        @Override
        public boolean authorizeSubscribe(RealtimePrincipal principal, String destination) {
            return true;
        }
    }
}
