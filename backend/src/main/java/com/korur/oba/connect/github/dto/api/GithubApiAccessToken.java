package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

/**
 * GitHub's answer to POST /app/installations/{id}/access_tokens: an installation token and the
 * moment it expires. The token is a credential: it is never logged, stored or returned.
 *
 * @param token     the installation access token
 * @param expiresAt when GitHub stops accepting the token
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiAccessToken(
        @JsonProperty("token") String token,
        @JsonProperty("expires_at") Instant expiresAt
) {

    /**
     * Keeps the token out of every log line and exception message that prints this record.
     */
    @Override
    public String toString() {
        return "GithubApiAccessToken[expiresAt=" + expiresAt + "]";
    }
}
