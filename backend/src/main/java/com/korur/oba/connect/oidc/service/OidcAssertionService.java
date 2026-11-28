package com.korur.oba.connect.oidc.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.oidc.dto.response.OidcDiscoveryResponse;

/**
 * The OpenID Connect issuer: signs short-lived assertions for a subject and serves the two public
 * documents a cloud provider reads to verify them.
 */
public interface OidcAssertionService {

    /**
     * Whether the deployment has an issuer that can sign.
     *
     * @return true when an issuer URL is set and a signing key is held
     */
    boolean isConfigured();

    /**
     * Signs an RS256 JWT for one subject and audience, valid for five minutes. The caller logs
     * which connection it signed for; the assertion itself is never logged.
     *
     * @param subject  the subject a federated credential trusts
     * @param audience the audience that federated credential names
     * @return the serialized JWT
     */
    String issue(String subject, String audience);

    /**
     * The discovery document of the issuer.
     *
     * @return the document, with jwks_uri at the issuer URL plus the key set path
     */
    OidcDiscoveryResponse getDiscovery();

    /**
     * The public key set of the issuer, signing key first.
     *
     * @return the key set as its JSON object
     */
    Map<String, Object> getJwks();
}
