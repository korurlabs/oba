package com.korur.oba.connect.routine.anthropic.config;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.anthropic.client.AnthropicRoutineClient;
import com.korur.oba.connect.routine.anthropic.constant.AnthropicRoutineConstant;
import com.korur.oba.connect.routine.anthropic.properties.AnthropicRoutineProperties;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.routine.anthropic.constant.config.AnthropicRoutineConfigLogConstant.*;

/**
 * Configuration of Anthropic routine fires.
 *
 * The client always exists, so the router has a client for
 * {@link com.korur.oba.connect.routine.core.enums.RoutineProvider#ANTHROPIC}; whether a routine fires
 * is decided by the agent that saved it, not by a switch here. The REST client is built here rather than taken from
 * the application's shared one, because Anthropic calls need their own base URL, headers and
 * timeouts, and it is not a bean, so no other code picks it up by type.
 */
@Configuration
@RequiredArgsConstructor
public class AnthropicRoutineConfig {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AnthropicRoutineConfig.class);

    private final LoggingService loggingService;

    /**
     * Creates the Anthropic routine properties bean.
     *
     * @return the bound properties
     */
    @Bean
    @ConfigurationProperties(prefix = AnthropicRoutineConstant.PROPERTIES_PREFIX)
    public AnthropicRoutineProperties anthropicRoutineProperties() {
        return new AnthropicRoutineProperties();
    }

    /**
     * Creates the Anthropic routine client and logs the base URL it calls.
     *
     * @param restClientBuilder Spring Boot's REST client builder, a new instance per injection
     * @param properties        the routine configuration
     * @return the client
     */
    @Bean
    public AnthropicRoutineClient anthropicRoutineClient(RestClient.Builder restClientBuilder,
                                                         AnthropicRoutineProperties properties) {
        loggingService.info(LOG_PREFIX, LogLayer.CONFIG, CLIENT_CREATED, properties.getBaseUrl());
        return new AnthropicRoutineClient(anthropicRestClient(restClientBuilder, properties), loggingService);
    }

    private RestClient anthropicRestClient(RestClient.Builder restClientBuilder,
                                           AnthropicRoutineProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
        return restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(AnthropicRoutineConstant.HEADER_BETA, properties.getBetaHeader())
                .defaultHeader(AnthropicRoutineConstant.HEADER_VERSION, properties.getVersion())
                .build();
    }
}
