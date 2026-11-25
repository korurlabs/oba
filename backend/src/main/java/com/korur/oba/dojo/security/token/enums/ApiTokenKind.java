package com.korur.oba.dojo.security.token.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Duration;
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.security.token.constant.ApiTokenConstant;

/**
 * The kind of an API token: who holds it and which prefix it carries. The prefix tells the token
 * chain which kind a bearer token claims to be before anything is looked up, and the stored kind
 * must match it.
 *
 * Mapped by name to the PostgreSQL enum apitoken_api_token_kind_enum, so a constant is never
 * renamed without a migration.
 */
public enum ApiTokenKind {

    // The Kaya recorder and CLI of one machine
    KAYA_MACHINE(ApiTokenConstant.PREFIX_KAYA_MACHINE, Duration.ofDays(ApiTokenConstant.KAYA_MACHINE_VALIDITY_DAYS)),
    // An agent user of one organization
    AGENT(ApiTokenConstant.PREFIX_AGENT, Duration.ofDays(ApiTokenConstant.AGENT_VALIDITY_DAYS));

    private final String prefix;
    private final Duration validity;

    ApiTokenKind(String prefix, Duration validity) {
        this.prefix = prefix;
        this.validity = validity;
    }

    public String getPrefix() {
        return prefix;
    }

    public Duration getValidity() {
        return validity;
    }

    /**
     * Finds the kind whose prefix a presented token starts with.
     *
     * @param token the presented value, possibly null
     * @return the kind, or empty when the value carries no known prefix
     */
    public static Optional<ApiTokenKind> fromToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        for (ApiTokenKind kind : values()) {
            if (token.startsWith(kind.prefix)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
