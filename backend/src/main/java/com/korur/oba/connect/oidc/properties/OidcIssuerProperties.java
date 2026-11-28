package com.korur.oba.connect.oidc.properties;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The OpenID Connect issuer that signs the assertions a cloud provider exchanges for an access
 * token of an organization's own identity. The deployment's secret store supplies the key; the
 * private key never leaves it and the backend process, because whoever holds it can sign the
 * subject of every connection of every organization.
 *
 * The issuer URL is permanent: every federated credential in every customer tenant names it, so a
 * change breaks all of them. During a key rotation the next or the replaced key is published next
 * to the signing key, so a provider that cached the key set still verifies every live assertion.
 *
 * @param issuerUrl          public https origin at which the API root is served, without a
 *                           trailing slash; blank turns the issuer off
 * @param signingKey         PKCS#8 RSA private key of at least 2048 bits, base64 DER or PEM;
 *                           required with an issuer URL except under the test profile, which
 *                           signs with a key generated at startup
 * @param signingKeyId       id of that key, the kid of every assertion
 * @param publishedPublicKey X.509 RSA public key, base64 DER or PEM, listed in the key set but
 *                           never used to sign; optional
 * @param publishedKeyId     id of the published key; set together with publishedPublicKey
 */
@ConfigurationProperties(prefix = "oba.oidc")
public record OidcIssuerProperties(
        String issuerUrl,
        String signingKey,
        String signingKeyId,
        String publishedPublicKey,
        String publishedKeyId
) {

    /**
     * Whether the deployment configured an issuer URL, a signing key and its id.
     */
    public boolean isConfigured() {
        return isSet(issuerUrl) && isSet(signingKey) && isSet(signingKeyId);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
