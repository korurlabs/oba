package com.korur.oba.dojo.security.token.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.security.token.entity.ApiToken;

/**
 * A token that was just issued, with its plaintext, which exists only here and is shown to its holder
 * once.
 *
 * @param token          the stored token
 * @param plaintext      the token to hand out; never logged or stored
 * @param revokedTokenId the token of the same kind and subject that the issue revoked, or null
 */
public record IssuedApiToken(
        ApiToken token,
        String plaintext,
        UUID revokedTokenId
) {

    /**
     * Leaves the plaintext out, so logging the record cannot leak the token.
     */
    @Override
    public String toString() {
        return "IssuedApiToken[tokenId=" + token.getId() + ", revokedTokenId=" + revokedTokenId + "]";
    }
}
