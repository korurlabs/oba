package com.korur.oba.connect.github.config;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;

// 4. Internal
import com.korur.oba.connect.github.client.GithubAppTokenProvider;
import com.korur.oba.connect.github.client.GithubClient;
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.mapper.GithubPayloadMapper;
import com.korur.oba.connect.github.properties.GithubProperties;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.github.constant.config.GithubConfigLogConstant.*;

/**
 * Configuration of the GitHub integration.
 *
 * The beans exist whether or not the keys are set, so the share domain can always ask
 * {@link GithubClient#isEnabledFor}; with a blank key that answers false and no GitHub call is made.
 * The REST client is built here rather than taken from the application's shared one, because
 * GitHub calls need their own base URL, headers and connect and read timeouts, and it is not a bean,
 * so no other code picks it up by type.
 */
@Configuration
@RequiredArgsConstructor
public class GithubConfig {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(GithubConfig.class);

    private final LoggingService loggingService;

    /**
     * Creates the GitHub App properties bean.
     *
     * @return the bound properties
     */
    @Bean
    @ConfigurationProperties(prefix = GithubConstant.PROPERTIES_PREFIX)
    public GithubProperties githubProperties() {
        return new GithubProperties();
    }

    /**
     * Creates the provider of installation tokens.
     *
     * @param restClientBuilder Spring Boot's REST client builder, a new instance per injection
     * @param properties        the App's configuration
     * @param clock             the application clock
     * @return the provider
     */
    @Bean
    public GithubAppTokenProvider githubAppTokenProvider(RestClient.Builder restClientBuilder,
                                                         GithubProperties properties, Clock clock) {
        return new GithubAppTokenProvider(githubRestClient(restClientBuilder), properties, clock, loggingService);
    }

    /**
     * Creates the GitHub REST client and logs whether the integration is on.
     *
     * @param restClientBuilder Spring Boot's REST client builder, a new instance per injection
     * @param tokenProvider     the provider of installation tokens
     * @param properties        the App's configuration
     * @param payloadMapper     maps GitHub's payloads
     * @param clock             the application clock
     * @return the client
     */
    @Bean
    public GithubClient githubClient(RestClient.Builder restClientBuilder, GithubAppTokenProvider tokenProvider,
                                     GithubProperties properties, GithubPayloadMapper payloadMapper, Clock clock) {
        if (properties.isConfigured()) {
            loggingService.info(LOG_PREFIX, LogLayer.CONFIG, INTEGRATION_ENABLED, properties.getOrganizationId());
        } else {
            loggingService.info(LOG_PREFIX, LogLayer.CONFIG, INTEGRATION_DISABLED);
        }
        return new GithubClient(githubRestClient(restClientBuilder), tokenProvider, properties, payloadMapper,
                clock, loggingService);
    }

    private RestClient githubRestClient(RestClient.Builder restClientBuilder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(GithubConstant.CONNECT_TIMEOUT)
                .withReadTimeout(GithubConstant.READ_TIMEOUT);
        return restClientBuilder
                .baseUrl(GithubConstant.API_BASE_URL)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.ACCEPT, GithubConstant.MEDIA_TYPE_GITHUB_JSON)
                .defaultHeader(GithubConstant.HEADER_API_VERSION, GithubConstant.API_VERSION)
                .build();
    }
}
