package com.korur.oba.connect.notification.config;


import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("emailMsGraphClientConfig")
@Data
public class MsGraphClientConfig {

    @Value("${microsoft.client.id}")
    private String clientId;

    @Value("${microsoft.client.secret}")
    private String clientSecret;

    @Value("${microsoft.tenant.id}")
    private String tenantId;

    @Value("${microsoft.scope}")
    private String scope;

    @Bean(name = "emailGraphServiceClient")
    public GraphServiceClient graphServiceClient() {
        return new GraphServiceClient(
                new ClientSecretCredentialBuilder()
                        .tenantId(tenantId)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .build(), scope);
    }
}
