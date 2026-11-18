package com.korur.oba.apps.kaya.session.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

// 3. Java SDK
import java.time.Duration;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractSessionIT;
import com.korur.oba.apps.kaya.support.SseTestClient;
import com.korur.oba.apps.kaya.support.SseTestClient.Frame;
import com.korur.oba.support.TestJwtDecoderConfiguration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The live stream on a real server (random port, real HTTP), so the servlet container's async
 * handling and Spring Security's treatment of the async dispatch run for real, which MockMvc does
 * not redispatch. The shared integration context provides the server and the one-second heartbeat,
 * so that the periodic send can be seen, and the server shuts down at once so that a connection the
 * server has not yet noticed as closed does not hold up the end of the test run for the
 * graceful-shutdown period.
 */
class SessionStreamIT extends AbstractSessionIT {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @LocalServerPort
    private int port;

    private SseTestClient open(Tenant tenant, Person person) {
        return SseTestClient.open(port, "/kaya/stream", Map.of(
                "Authorization", "Bearer " + TestJwtDecoderConfiguration.tokenOf(person.keycloakId(), person.email()),
                "X-Organization-ID", tenant.id()));
    }

    private void awaitOpen(SseTestClient stream) {
        await().atMost(WAIT).until(() -> !stream.framesNamed("heartbeat").isEmpty());
    }

    private void awaitSessionUpdated(SseTestClient stream, String session) {
        await().atMost(WAIT).until(() -> stream.framesNamed("session-updated").stream()
                .anyMatch(frame -> frame.data().contains("\"" + session + "\"")));
    }

    private void awaitNoSessionUpdated(SseTestClient stream) {
        await().during(Duration.ofMillis(700)).atMost(WAIT)
                .until(() -> stream.framesNamed("session-updated").isEmpty());
    }

    @Test
    void open_answersEventStreamAndHeartbeatAtOnce() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");

        try (SseTestClient stream = open(tenant, ada)) {
            awaitOpen(stream);

            assertEquals(200, stream.status());
            assertTrue(stream.contentType().startsWith(MediaType.TEXT_EVENT_STREAM_VALUE), stream.contentType());
            assertTrue(stream.framesNamed("heartbeat").get(0).data().contains("\"at\""), stream.frames().toString());
        }
    }

    @Test
    void heartbeat_repeatsWhileTheStreamIsIdle() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");

        try (SseTestClient stream = open(tenant, ada)) {
            await().atMost(WAIT).until(() -> stream.framesNamed("heartbeat").size() >= 3);

            assertTrue(stream.framesNamed("session-updated").isEmpty());
        }
    }

    @Test
    void ingestBatch_sendsSessionUpdatedToTheOrganizationsStream() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");

        try (SseTestClient stream = open(tenant, ada)) {
            awaitOpen(stream);

            String session = ingestSession(laptop, remote(), 3);

            awaitSessionUpdated(stream, session);
            Frame update = stream.framesNamed("session-updated").get(0);
            assertTrue(update.data().contains("\"sessionId\":\"" + session + "\""), update.data());
            assertTrue(update.data().contains("\"lastEventAt\":\"" + BASE_TIME.plusSeconds(3) + "\""), update.data());
        }
    }

    @Test
    void ingestBatch_isNeverSentToAnotherOrganizationsStream() throws Exception {
        Tenant tenant = newTenant();
        Tenant other = newTenant();
        Person ada = signIn(tenant, "ada");
        Person bob = signIn(other, "bob");
        Machine laptop = registerMachine(tenant, ada, "laptop");

        try (SseTestClient own = open(tenant, ada); SseTestClient foreign = open(other, bob)) {
            awaitOpen(own);
            awaitOpen(foreign);

            String session = ingestSession(laptop, remote(), 2);

            awaitSessionUpdated(own, session);
            awaitNoSessionUpdated(foreign);
            assertTrue(foreign.frames().stream().noneMatch(frame -> frame.data().contains(session)), foreign.frames().toString());
        }
    }

    @Test
    void severalStreamsOfOneOrganization_allReceiveTheEvent() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Person cy = signIn(tenant, "cy");
        Machine laptop = registerMachine(tenant, ada, "laptop");

        try (SseTestClient first = open(tenant, ada); SseTestClient second = open(tenant, cy)) {
            awaitOpen(first);
            awaitOpen(second);

            String session = ingestSession(laptop, remote(), 1);

            awaitSessionUpdated(first, session);
            awaitSessionUpdated(second, session);
        }
    }

    @Test
    void laterBatchOfTheSameSession_announcesTheSessionAgain() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        String remote = remote();
        String session = ingestSession(laptop, remote, 2);

        try (SseTestClient stream = open(tenant, ada)) {
            awaitOpen(stream);

            send(laptop.token(), batch(laptop, session, remote, events(3, 4))).andExpect(status().isOk());

            awaitSessionUpdated(stream, session);
        }
    }

    @Test
    void linkAddLinkRemoveAndSessionDelete_eachSendSessionUpdated() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        String session = ingestSession(laptop, remote(), 2);

        try (SseTestClient stream = open(tenant, ada)) {
            awaitOpen(stream);

            String linkId = read(mockMvc.perform(in(tenant, post("/kaya/sessions/" + session + "/links"), ada.jwt(),
                            "{\"kind\":\"KAZAN_TICKET\",\"reference\":\"SEN-1\"}"))
                    .andExpect(status().isCreated()).andReturn(), "$.data.id");
            await().atMost(WAIT).until(() -> stream.framesNamed("session-updated").size() >= 1);

            mockMvc.perform(in(tenant, delete("/kaya/sessions/" + session + "/links/" + linkId), ada.jwt(), null)).andExpect(status().isOk());
            await().atMost(WAIT).until(() -> stream.framesNamed("session-updated").size() >= 2);

            mockMvc.perform(in(tenant, delete("/kaya/sessions/" + session), ada.jwt(), null)).andExpect(status().isOk());
            await().atMost(WAIT).until(() -> stream.framesNamed("session-updated").size() >= 3);

            List<Frame> updates = stream.framesNamed("session-updated");
            assertTrue(updates.get(0).data().contains("\"lastEventAt\":\"" + BASE_TIME.plusSeconds(2) + "\""), updates.get(0).data());
            assertTrue(updates.get(2).data().contains("\"sessionId\":\"" + session + "\""), updates.get(2).data());
            assertTrue(updates.get(2).data().contains("\"lastEventAt\":null") || !updates.get(2).data().contains("lastEventAt\":\"2"),
                    "a delete carries no last event time: " + updates.get(2).data());
        }
    }

    @Test
    void rejectedLink_sendsNothing() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");
        String session = ingestSession(laptop, remote(), 1);

        try (SseTestClient stream = open(tenant, ada)) {
            awaitOpen(stream);

            mockMvc.perform(in(tenant, post("/kaya/sessions/" + session + "/links"), ada.jwt(),
                    "{\"kind\":\"KAZAN_TICKET\",\"reference\":\"nope\"}")).andExpect(status().isUnprocessableEntity());

            awaitNoSessionUpdated(stream);
        }
    }

    @Test
    void closedStream_isDroppedAndLaterEventsStillReachTheOthers() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");
        Machine laptop = registerMachine(tenant, ada, "laptop");

        try (SseTestClient staying = open(tenant, ada)) {
            SseTestClient leaving = open(tenant, ada);
            awaitOpen(staying);
            awaitOpen(leaving);
            leaving.close();

            String first = ingestSession(laptop, remote(), 1);
            String second = ingestSession(laptop, remote(), 1);

            awaitSessionUpdated(staying, first);
            awaitSessionUpdated(staying, second);
            assertNull(staying.failure());
        }
    }

    @Test
    void withoutJwt_answers401() throws Exception {
        Tenant tenant = newTenant();

        try (SseTestClient stream = SseTestClient.open(port, "/kaya/stream", Map.of("X-Organization-ID", tenant.id()))) {
            await().atMost(WAIT).until(() -> stream.status() != 0);

            assertEquals(401, stream.status());
        }
    }

    @Test
    void memberOfAnotherOrganization_answers403() throws Exception {
        Tenant tenant = newTenant();
        Tenant other = newTenant();
        Person bob = signIn(other, "bob");

        try (SseTestClient stream = open(tenant, bob)) {
            await().atMost(WAIT).until(() -> stream.status() != 0);

            assertEquals(403, stream.status());
        }
    }

    @Test
    void withoutOrganizationHeader_answers400() throws Exception {
        Tenant tenant = newTenant();
        Person ada = signIn(tenant, "ada");

        try (SseTestClient stream = SseTestClient.open(port, "/kaya/stream",
                Map.of("Authorization", "Bearer " + TestJwtDecoderConfiguration.tokenOf(ada.keycloakId(), ada.email())))) {
            await().atMost(WAIT).until(() -> stream.status() != 0);

            assertEquals(400, stream.status());
        }
    }
}
