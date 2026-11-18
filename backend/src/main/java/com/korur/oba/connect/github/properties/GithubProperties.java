package com.korur.oba.connect.github.properties;

// 1. Framework-level
import org.springframework.util.StringUtils;

// 2. Third-party
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Configuration of the GitHub App, bound from the prefix "oba.integrations.github".
 *
 * Every key defaults to empty so the application starts without the integration: while any key
 * is blank the feature is off, and no GitHub call is made. One App installation serves exactly
 * one organization, so the organization is configuration, not data.
 */
@Getter
@Setter
public class GithubProperties {

    // The App's ID, the issuer of its JWT.
    private String appId;

    // The installation on the korurlabs account whose repositories the application reads.
    private String installationId;

    // Base64 of the App's PKCS#8 PEM private key. Never logged; class has no toString on purpose.
    private String privateKey;

    // The only organization allowed to use the installation.
    private UUID organizationId;

    /**
     * Tells whether every key is set.
     *
     * @return true when the App can authenticate and an organization is named
     */
    public boolean isConfigured() {
        return StringUtils.hasText(appId)
                && StringUtils.hasText(installationId)
                && StringUtils.hasText(privateKey)
                && organizationId != null;
    }

    /**
     * Tells whether the given organization may use the installation.
     *
     * @param candidateOrganizationId the organization of the request
     * @return true when the integration is configured for exactly this organization
     */
    public boolean isEnabledFor(UUID candidateOrganizationId) {
        return isConfigured() && organizationId.equals(candidateOrganizationId);
    }
}
