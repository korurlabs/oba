package com.korur.oba.connect.realtime.config;

// 1. Framework-level
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

// 2. Third-party

// 3. Java SDK
import java.util.concurrent.Executor;

// 4. Internal
import com.korur.oba.connect.realtime.constant.RealtimeConstant;
import com.korur.oba.connect.realtime.interceptor.RealtimeInboundInterceptor;
import com.korur.oba.connect.realtime.properties.RealtimeProperties;
import com.korur.oba.connect.realtime.service.RealtimeSessionRegistry;
import com.korur.oba.connect.realtime.service.RealtimeTicketService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.config.CorsConfig;

/**
 * The realtime endpoint: STOMP 1.2 over a plain WebSocket at /ws, with the broker that
 * oba.realtime.broker names for the organization topics and the per-user queues. Only the
 * in-memory simple broker is built, which holds for one replica; any other value stops the
 * application at startup rather than run without a broker.
 *
 * No SockJS: every supported browser has a native WebSocket. The handshake is guarded by
 * {@link RealtimeTicketService}, which accepts only a one-time ticket issued over authenticated
 * REST. The inbound channel is guarded by {@link RealtimeInboundInterceptor}, which binds CONNECT
 * to the ticket's principal and confines SUBSCRIBE and SEND. The broker's selector support is
 * off: a selector would filter messages on the broker with an expression the client chose,
 * outside the per-destination authorization.
 *
 * The handshake is public at the HTTP security layer (the browser cannot send an Authorization
 * header on it) and the ticket is its only credential, so the transport limits are tight: a
 * socket that never sends CONNECT is closed after ten seconds, and a client frame may not exceed
 * 64 KiB. A slow client whose outbound buffer passes 512 KiB is dropped rather than holding the
 * broker. Every session passes through {@link RealtimeSessionRegistry}, which tracks presence and
 * closes sessions whose token expired or whose access was revoked.
 *
 * The handshake accepts exactly the origins the HTTP CORS configuration accepts, taken from
 * {@link CorsConfig}: the Sensey domains, and the local origins only when
 * sensey.cors.allow-local-origins is on, which {@link CorsConfig} refuses under the prod profile.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RealtimeWebSocketConfig.class);

    private static final int TIME_TO_FIRST_MESSAGE_MS = 10_000;
    private static final int MESSAGE_SIZE_LIMIT_BYTES = 64 * 1024;
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private static final int EXECUTOR_CORE_POOL_SIZE = 2;
    private static final int EXECUTOR_MAX_POOL_SIZE = 4;
    private static final int EXECUTOR_QUEUE_CAPACITY = 500;

    private final RealtimeProperties realtimeProperties;
    private final RealtimeInboundInterceptor realtimeInboundInterceptor;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final RealtimeTicketService realtimeTicketService;
    private final LoggingService loggingService;
    private final CorsConfig corsConfig;

    public RealtimeWebSocketConfig(
            RealtimeProperties realtimeProperties,
            RealtimeInboundInterceptor realtimeInboundInterceptor,
            RealtimeSessionRegistry realtimeSessionRegistry,
            RealtimeTicketService realtimeTicketService,
            LoggingService loggingService,
            CorsConfig corsConfig) {
        this.realtimeProperties = realtimeProperties;
        this.realtimeInboundInterceptor = realtimeInboundInterceptor;
        this.realtimeSessionRegistry = realtimeSessionRegistry;
        this.realtimeTicketService = realtimeTicketService;
        this.loggingService = loggingService;
        this.corsConfig = corsConfig;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        if (!realtimeProperties.isEnabled()) {
            loggingService.warn(LOG_PREFIX, LogLayer.CONFIG, RealtimeConstant.ENDPOINT_DISABLED);
            return;
        }
        registry.addEndpoint(RealtimeConstant.ENDPOINT_PATH)
                .addInterceptors(realtimeTicketService)
                .setAllowedOriginPatterns(corsConfig.allowedOriginPatterns().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (!RealtimeConstant.BROKER_SIMPLE.equals(realtimeProperties.getBroker())) {
            throw new IllegalStateException(RealtimeConstant.BROKER_UNSUPPORTED.formatted(realtimeProperties.getBroker()));
        }
        long heartbeatMs = realtimeProperties.getHeartbeatMs();
        registry.enableSimpleBroker(RealtimeConstant.BROKER_TOPIC_PREFIX, RealtimeConstant.BROKER_QUEUE_PREFIX)
                .setHeartbeatValue(new long[] {heartbeatMs, heartbeatMs})
                .setTaskScheduler(realtimeHeartbeatTaskScheduler())
                .setSelectorHeaderName(null);
        registry.setApplicationDestinationPrefixes(RealtimeConstant.APPLICATION_DESTINATION_PREFIX);
        registry.setUserDestinationPrefix(RealtimeConstant.USER_DESTINATION_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(realtimeInboundInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setTimeToFirstMessage(TIME_TO_FIRST_MESSAGE_MS);
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT_BYTES);
        registration.setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT_BYTES);
        registration.addDecoratorFactory(realtimeSessionRegistry);
    }

    /**
     * Scheduler of the broker heartbeats, kept apart from the application's scheduled jobs so a
     * slow job can never delay a heartbeat into a proxy idle timeout, and the reverse.
     *
     * Not a default candidate, so it is never injected by type and is not picked up as the
     * scheduler of the {@link org.springframework.scheduling.annotation.Scheduled} jobs.
     *
     * @return the heartbeat scheduler, one thread
     */
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler realtimeHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(RealtimeConstant.HEARTBEAT_THREAD_PREFIX);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /**
     * Executor of the after-commit realtime fan-out, so computing and sending signals never runs
     * on a request thread.
     *
     * At most four threads, because each fan-out may hold one of the ten Hikari connections for
     * its unread query and the request threads need the rest. The queue is deep because a burst of
     * messages produces a burst of fan-outs that are each short.
     *
     * @return the fan-out executor
     */
    @Bean(name = RealtimeConstant.EXECUTOR_BEAN_NAME)
    public Executor realtimeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(EXECUTOR_CORE_POOL_SIZE);
        executor.setMaxPoolSize(EXECUTOR_MAX_POOL_SIZE);
        executor.setQueueCapacity(EXECUTOR_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(RealtimeConstant.EXECUTOR_THREAD_PREFIX);
        executor.initialize();
        return executor;
    }
}
