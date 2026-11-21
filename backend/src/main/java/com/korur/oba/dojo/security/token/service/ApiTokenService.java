package com.korur.oba.dojo.security.token.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.security.token.dto.IssuedApiToken;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Issues, revokes and marks the use of API tokens. The caller checks who may do so and writes its
 * own audit entry; this service never returns a plaintext token except from {@link #issue}.
 */
public interface ApiTokenService {

    /**
     * Issues a token for a subject and revokes its live token of the same kind, so a subject holds at
     * most one. The token expires after the validity of its kind.
     *
     * @param kind           the kind of the token
     * @param subjectId      the machine or agent user the token stands for
     * @param owner          the user the token acts as
     * @param organizationId the only organization the token may act in
     * @return the stored token, its plaintext and the id of the revoked token, if any
     */
    IssuedApiToken issue(ApiTokenKind kind, UUID subjectId, User owner, UUID organizationId);

    /**
     * Revokes the live token of a subject.
     *
     * @param kind      the kind of the token
     * @param subjectId the machine or agent user
     * @return the revoked token, or empty when the subject had no live token
     */
    Optional<ApiToken> revoke(ApiTokenKind kind, UUID subjectId);

    /**
     * Revokes every live token a user owns in an organization, such as when the user leaves it.
     *
     * @param organizationId the organization
     * @param userId         the owner
     * @return the number of tokens revoked
     */
    int revokeAllOfMember(UUID organizationId, UUID userId);

    /**
     * Records that a token authenticated a request, at most once a minute.
     *
     * @param tokenId the token
     */
    void markUsed(UUID tokenId);
}
