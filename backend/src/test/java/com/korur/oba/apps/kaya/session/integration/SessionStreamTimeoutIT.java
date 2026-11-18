package com.korur.oba.apps.kaya.session.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

// 3. Java SDK
import java.time.Duration;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractSessionIT;
import com.korur.oba.apps.kaya.support.SseTestClient;
import com.korur.oba.support.TestJwtDecoderConfiguration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end of a stream on a real server. The timeout is shortened to two seconds so that the
 * container's async timeout and the dispatch that follows it, which Spring Security sees again,
 * can be observed: the stream must end as a finished response, not as a refused or broken one.
 * This class keeps a context of its own, because a two-second timeout would end the longer-lived
 * streams of the other stream tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"kaya.session.stream.timeout=PT2S", "kaya.session.stream.heartbeat=PT1S"})
@Import(TestJwtDecoderConfiguration.class)
class SessionStreamTimeoutIT extends AbstractSessionIT {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @LocalServerPort
    private int port;

    private SseTestClient open(Tenant tenant, Person person) {
        return SseTestClient.open(port, "/kaya/stream", Map.of(
                "Authorization", "Bearer " + TestJwtDecoderConfiguration.tokenOf(person.keycloakId(), person.email()),
                "X-Organization-ID", tenant.id()));
    }

    @Test
    void stream_atTheTimeout_endsAsAFinishedResponse() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");

        try (SseTestClient stream = open(tenant, ada)) {
            await().atMost(WAIT).until(stream::ended);

            assertEquals(200, stream.status());
            assertNull(stream.failure(), "the stream ended with an error");
            assertTrue(!stream.framesNamed("heartbeat").isEmpty());
        }
    }

    @Test
    void afterTheTimeout_aNewStreamOpensAndReceivesEvents() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        try (SseTestClient first = open(tenant, ada)) {
            await().atMost(WAIT).until(first::ended);
        }

        try (SseTestClient second = open(tenant, ada)) {
            await().atMost(WAIT).until(() -> !second.framesNamed("heartbeat").isEmpty());
            String session = ingestSession(laptop, remote(), 1);

            await().atMost(WAIT).until(() -> second.framesNamed("session-updated").stream()
                    .anyMatch(frame -> frame.data().contains(session)));
        }
    }
}
