package com.korur.oba.dojo.security.token.dto;

// 3. Java SDK
import java.util.UUID;

/**
 * The caller of a token whose subject is a user itself, such as an agent: the request acts as that user
 * in the token's organization, with nothing beyond the three ids the access check reads.
 *
 * @param tokenId        the token that authenticated the request
 * @param userId         the user the request acts as
 * @param organizationId the only organization the token may act in
 */
public record UserApiTokenPrincipal(
        UUID tokenId,
        UUID userId,
        UUID organizationId
) implements ApiTokenPrincipal {
}
