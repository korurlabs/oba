package com.korur.oba.connect.azure.mock_data;

// 1. Framework-level
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.entity.AzureConnection;

/**
 * Test data of the Azure connection and Resource Manager tests. Every id is random, so no test
 * depends on a real tenant or subscription.
 */
public final class AzureConnectionMocks {

    public static final String TOKEN = "arm-access-token";
    public static final String SUBSCRIPTIONS_URL =
            "https://management.azure.com/subscriptions?api-version=2022-12-01";

    private AzureConnectionMocks() {
        throw new UnsupportedOperationException("Mock class should not be instantiated");
    }

    public static AzureConnection connection() {
        return connection(UUID.randomUUID());
    }

    public static AzureConnection connection(UUID clientId) {
        UUID organizationId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        AzureConnection connection = AzureConnection.builder()
                .name("Production")
                .tenantId(UUID.randomUUID())
                .clientId(clientId)
                .subject(String.format(AzureConnectionConstant.SUBJECT_FORMAT, organizationId, connectionId))
                .organizationId(organizationId)
                .build();
        connection.setId(connectionId);
        return connection;
    }

    public static RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
    }

    public static String subscription(UUID subscriptionId, String displayName, String state) {
        return "{\"id\":\"/subscriptions/" + subscriptionId + "\",\"subscriptionId\":\"" + subscriptionId
                + "\",\"displayName\":\"" + displayName + "\",\"state\":\"" + state
                + "\",\"tenantId\":\"" + UUID.randomUUID() + "\",\"tags\":{\"team\":\"ops\"}}";
    }

    public static String subscriptionPage(String nextLink, String... subscriptions) {
        String next = nextLink == null ? "" : ",\"nextLink\":\"" + nextLink + "\"";
        return "{\"value\":[" + String.join(",", subscriptions) + "]" + next + "}";
    }
}
