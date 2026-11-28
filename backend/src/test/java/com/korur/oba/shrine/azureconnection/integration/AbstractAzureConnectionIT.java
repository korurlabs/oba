package com.korur.oba.shrine.azureconnection.integration;

// 1. Framework-level
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 3. Java SDK
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.connect.azure.client.AzureResourceManagerClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base of the Azure connection integration tests: the application with an OpenID Connect issuer
 * switched on, so connections can be created, and the Resource Manager client replaced by a mock,
 * so no test reaches Azure. The token exchange sits behind that client, so a refused exchange is
 * stubbed as the error the client would throw.
 */
abstract class AbstractAzureConnectionIT extends AbstractTenantIT {

    protected static final String ISSUER_URL = "https://oba.it.example";
    protected static final String SIGNING_KEY_ID = "azure-it-key";
    protected static final String CONNECTIONS_PATH = "/azure/connections";

    @MockitoBean
    protected AzureResourceManagerClient azureResourceManagerClient;

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String privateKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        registry.add("oba.oidc.issuer-url", () -> ISSUER_URL);
        registry.add("oba.oidc.signing-key", () -> privateKey);
        registry.add("oba.oidc.signing-key-id", () -> SIGNING_KEY_ID);
    }

    protected static String connectionBody(String name, UUID tenantId, UUID clientId) {
        String client = clientId == null ? "null" : "\"" + clientId + "\"";
        return "{\"name\":\"" + name + "\",\"tenantId\":\"" + tenantId + "\",\"clientId\":" + client + "}";
    }

    /** Creates a connection in the tenant with the given sign-in and returns its id. */
    protected String createConnection(Tenant tenant, RequestPostProcessor jwt, String name, UUID tenantId, UUID clientId)
            throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(CONNECTIONS_PATH), jwt,
                        connectionBody(name, tenantId, clientId)))
                .andExpect(status().isCreated()).andReturn();
        return read(result, "$.data.id");
    }
}
