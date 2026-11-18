package com.korur.oba.connect.push.config;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.concurrent.Executor;

// 4. Internal
import com.korur.oba.connect.push.client.ExpoPushClient;
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.properties.ExpoProperties;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.push.constant.config.PushConfigLogConstant.*;

/**
 * Configuration of push sending: the Expo properties, the Expo client and the executor that push
 * dispatch runs on.
 *
 * The client exists whether or not the access token is set, so dispatch can always ask
 * {@link ExpoPushClient#isEnabled}; with a blank token that answers false, no Expo call is made, and
 * one warning at startup says so. The REST client is built here rather than taken from the
 * application's shared one, because Expo calls need their own base URL and timeouts, and it is not a
 * bean, so no other code picks it up by type.
 */
@Configuration
@RequiredArgsConstructor
public class PushConfig {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PushConfig.class);

    private static final int EXECUTOR_CORE_POOL_SIZE = 2;
    private static final int EXECUTOR_MAX_POOL_SIZE = 4;
    private static final int EXECUTOR_QUEUE_CAPACITY = 500;

    private final LoggingService loggingService;

    /**
     * Creates the Expo properties bean.
     *
     * @return the bound properties
     */
    @Bean
    @ConfigurationProperties(prefix = PushConstant.EXPO_PROPERTIES_PREFIX)
    public ExpoProperties expoProperties() {
        return new ExpoProperties();
    }

    /**
     * Creates the Expo Push Service client and logs whether push sending is on.
     *
     * @param restClientBuilder Spring Boot's REST client builder, a new instance per injection
     * @param properties        the Expo configuration
     * @return the client
     */
    @Bean
    public ExpoPushClient expoPushClient(RestClient.Builder restClientBuilder, ExpoProperties properties) {
        if (properties.isConfigured()) {
            loggingService.info(LOG_PREFIX, LogLayer.CONFIG, PUSH_ENABLED);
        } else {
            loggingService.warn(LOG_PREFIX, LogLayer.CONFIG, PUSH_DISABLED);
        }
        return new ExpoPushClient(expoRestClient(restClientBuilder), properties, loggingService);
    }

    /**
     * Executor of push dispatch, so building and sending pushes never runs on a request thread.
     *
     * At most four threads, because each dispatch holds one of the ten Hikari connections while it
     * reads devices and switches, and the request threads need the rest. When the queue is full the
     * push is dropped and logged at error level instead of failing the caller, whose message,
     * incident or nudge is already committed.
     *
     * @return the dispatch executor
     */
    @Bean(name = PushConstant.EXECUTOR_BEAN_NAME)
    public Executor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(EXECUTOR_CORE_POOL_SIZE);
        executor.setMaxPoolSize(EXECUTOR_MAX_POOL_SIZE);
        executor.setQueueCapacity(EXECUTOR_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(PushConstant.EXECUTOR_THREAD_PREFIX);
        executor.setRejectedExecutionHandler((task, pool) -> loggingService.error(LOG_PREFIX, LogLayer.CONFIG,
                EXECUTOR_REJECTED, EXECUTOR_QUEUE_CAPACITY));
        executor.initialize();
        return executor;
    }

    private RestClient expoRestClient(RestClient.Builder restClientBuilder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(PushConstant.EXPO_CONNECT_TIMEOUT)
                .withReadTimeout(PushConstant.EXPO_READ_TIMEOUT);
        return restClientBuilder
                .baseUrl(PushConstant.EXPO_API_BASE_URL)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
