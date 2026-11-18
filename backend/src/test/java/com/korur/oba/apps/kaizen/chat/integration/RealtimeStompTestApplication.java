package com.korur.oba.apps.kaizen.chat.integration;

// 1. Framework-level
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

// 2. Third-party

// 3. Java SDK
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.helper.ChatRealtimeDestinationAuthorizer;
import com.korur.oba.connect.realtime.config.RealtimeWebSocketConfig;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.dto.RealtimeSignal;
import com.korur.oba.connect.realtime.event.RealtimePresenceChangedEvent;
import com.korur.oba.connect.realtime.helper.RealtimeDestinationAuthorizerRegistry;
import com.korur.oba.connect.realtime.interceptor.RealtimeInboundInterceptor;
import com.korur.oba.connect.realtime.service.InMemoryRealtimeTicketStore;
import com.korur.oba.connect.realtime.service.RealtimeDestinationAuthorizer;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeSignalBus;
import com.korur.oba.connect.realtime.service.RealtimeTicketService;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.dojo.config.SchedulingConfig;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.config.CorsConfig;

/**
 * A slice of the application that holds only the realtime stack: the STOMP endpoint with the
 * handshake ticket service and its in-memory store, the inbound interceptor, the destination
 * authorizer registry with chat's authorizer and a scoped test consumer, the session registry
 * with its inbound limits, the signal bus, a presence relay, the shared scheduler and a stub
 * receiver of the typing destination. No DataSource, JPA, Flyway or security filter chain is
 * configured, so the context starts without a database or Docker. The organization access check
 * the ticket service reaches is supplied as a mock by the test.
 *
 * It is marked {@link TestComponent}, so the component scan of the other Spring Boot tests skips
 * it and its nested stub receiver instead of adding them to the full application. It stays a
 * plain {@link Configuration}, not a TestConfiguration, because the slice tests start from it
 * alone, without the application's own configuration.
 */
@Configuration
@TestComponent
@EnableScheduling
@ImportAutoConfiguration({
        ServletWebServerFactoryAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        WebSocketServletAutoConfiguration.class,
        WebSocketMessagingAutoConfiguration.class,
        TaskExecutionAutoConfiguration.class,
        TaskSchedulingAutoConfiguration.class
})
@Import({
        RealtimeWebSocketConfig.class,
        RealtimeInboundInterceptor.class,
        RealtimeSessionRegistry.class,
        RealtimeDestinationAuthorizerRegistry.class,
        RealtimeSignalBus.class,
        RealtimeTicketService.class,
        InMemoryRealtimeTicketStore.class,
        ChatRealtimeDestinationAuthorizer.class,
        RealtimeStompTestApplication.PresenceRelay.class,
        RealtimeStompTestApplication.ScopedAuthorizer.class,
        LoggingService.class,
        CorsConfig.class,
        SchedulingConfig.class,
        RealtimeStompTestApplication.TypingStub.class
})
public class RealtimeStompTestApplication {

    /**
     * Stands in for the typing receiver of the chat module, so a permitted SEND has a handler.
     */
    @Controller
    public static class TypingStub {

        private final AtomicInteger received = new AtomicInteger();

        @MessageMapping("/chat/typing")
        public void typing(String body) {
            received.incrementAndGet();
        }

        public int receivedCount() {
            return received.get();
        }
    }

    /**
     * A second consumer whose topic is scoped per user, as board topics will be: it claims
     * "/topic/organizations/{organizationId}/scoped" and denies the users in denied.
     */
    public static class ScopedAuthorizer implements RealtimeDestinationAuthorizer {

        public static final String SUFFIX = "/scoped";

        private final Set<UUID> denied = ConcurrentHashMap.newKeySet();
        private final AtomicInteger subscribeChecks = new AtomicInteger();

        @Override
        public String consumer() {
            return "scoped-test";
        }

        @Override
        public boolean supportsSubscribe(String destination) {
            return RealtimeDestinationUtil.parseOrganizationTopic(destination, SUFFIX).isPresent();
        }

        @Override
        public boolean authorizeSubscribe(RealtimePrincipal principal, String destination) {
            subscribeChecks.incrementAndGet();
            return !denied.contains(principal.userId());
        }

        public void deny(UUID userId) {
            denied.add(userId);
        }

        public void reset() {
            denied.clear();
        }

        public int subscribeChecks() {
            return subscribeChecks.get();
        }
    }

    /**
     * Turns each presence change into a PRESENCE signal on the organization's chat topic, as the
     * chat module does, so a test can watch presence over the socket.
     */
    @Component
    public static class PresenceRelay {

        private final RealtimeSignalBus realtimeSignalBus;

        public PresenceRelay(RealtimeSignalBus realtimeSignalBus) {
            this.realtimeSignalBus = realtimeSignalBus;
        }

        @EventListener
        public void onPresenceChanged(RealtimePresenceChangedEvent event) {
            realtimeSignalBus.publishToTopic(
                    RealtimeDestinationUtil.organizationTopic(event.organizationId(), ChatConstant.CHAT_TOPIC_SUFFIX),
                    new PresenceSignal("PRESENCE", event.userId(), event.online()));
        }
    }

    /**
     * The presence frame the relay publishes.
     *
     * @param type   always "PRESENCE"
     * @param userId the member
     * @param online whether the member came online
     */
    public record PresenceSignal(String type, UUID userId, boolean online) implements RealtimeSignal {
    }

    /**
     * The signal the tests publish; the clients keep only TEST and PRESENCE frames.
     *
     * @param type always "TEST"
     * @param mark what the test looks for
     */
    public record TestSignal(String type, String mark) implements RealtimeSignal {

        public static TestSignal of(String mark) {
            return new TestSignal("TEST", mark);
        }
    }
}
