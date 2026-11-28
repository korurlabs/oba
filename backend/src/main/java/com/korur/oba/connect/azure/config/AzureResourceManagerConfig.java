package com.korur.oba.connect.azure.config;

// 1. Framework-level
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
import com.korur.oba.connect.azure.client.AzureConnectionTokenProvider;
import com.korur.oba.connect.azure.client.AzureResourceManagerClient;
import com.korur.oba.connect.azure.constant.AzureResourceManagerConstant;
import com.korur.oba.connect.azure.mapper.AzureResourceManagerPayloadMapper;
import com.korur.oba.connect.oidc.service.OidcAssertionService;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Configuration of the Azure Resource Manager reads made through the connections of an
 * organization.
 *
 * The beans exist whether or not the deployment has an OpenID Connect issuer; without one no
 * connection can be created, so no token is ever asked for. The REST client is built here rather
 * than taken from the application's shared one, because Resource Manager calls need their own
 * connect and read timeouts, and it is not a bean, so no other code picks it up by type.
 */
@Configuration
@RequiredArgsConstructor
public class AzureResourceManagerConfig {

    private final LoggingService loggingService;

    /**
     * Creates the provider of tokens per connection.
     *
     * @param oidcAssertionService the issuer that signs the assertions
     * @return the provider
     */
    @Bean
    public AzureConnectionTokenProvider azureConnectionTokenProvider(OidcAssertionService oidcAssertionService) {
        return new AzureConnectionTokenProvider(oidcAssertionService, loggingService);
    }

    /**
     * Creates the Resource Manager client.
     *
     * @param restClientBuilder Spring Boot's REST client builder, a new instance per injection
     * @param tokenProvider     the provider of tokens per connection
     * @param payloadMapper     maps Resource Manager's payloads
     * @return the client
     */
    @Bean
    public AzureResourceManagerClient azureResourceManagerClient(RestClient.Builder restClientBuilder,
                                                                 AzureConnectionTokenProvider tokenProvider,
                                                                 AzureResourceManagerPayloadMapper payloadMapper) {
        return new AzureResourceManagerClient(armRestClient(restClientBuilder), tokenProvider, payloadMapper,
                loggingService);
    }

    private RestClient armRestClient(RestClient.Builder restClientBuilder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(AzureResourceManagerConstant.CONNECT_TIMEOUT)
                .withReadTimeout(AzureResourceManagerConstant.READ_TIMEOUT);
        return restClientBuilder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
