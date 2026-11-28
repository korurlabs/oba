package com.korur.oba.connect.azure.dto;

// 1. Framework-level

// 2. Third-party
import com.azure.core.credential.TokenCredential;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The credential of one connection together with the identity it was built for. The credential
 * holds Azure Resource Manager's token and renews it; the identity fields tell whether the row
 * still names the same tenant, client and subject, and the two ids name the connection in the log
 * line of every assertion the credential asks for.
 *
 * @param organizationId the organization of the connection
 * @param connectionId   the connection
 * @param tenantId       the tenant the credential exchanges assertions in
 * @param clientId       the client id of the identity
 * @param subject        the subject of the assertions
 * @param credential     the credential, which signs an assertion for each token exchange
 */
public record AzureCachedCredential(
        UUID organizationId,
        UUID connectionId,
        UUID tenantId,
        UUID clientId,
        String subject,
        TokenCredential credential
) {
}
