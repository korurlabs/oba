package com.korur.oba.dojo.security.token.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

/**
 * Looks up API tokens: the token of a request, and the live tokens of the subjects a list shows.
 */
public interface ApiTokenQueryService {

    /**
     * Resolves a presented token. The token is hashed and looked up by its hash on the unique index, so
     * the plaintext is never compared or stored. Each refusal is logged with its reason and the token
     * id, never the token.
     *
     * @param token the plaintext token from the Authorization header, without "Bearer "
     * @return the live token
     * @throws com.korur.oba.dojo.exception.internal.auth.UnauthorizedException GEN_AUT_001 when the
     *         value carries no known prefix, is malformed, unknown, of another kind than its prefix,
     *         revoked or expired; the token chain answers it with the 401 of the kind
     */
    ApiToken authenticate(String token);

    /**
     * Finds the live tokens of subjects of one kind, to show their prefix and last use.
     *
     * @param kind       the kind of the tokens
     * @param subjectIds the machines or agent users
     * @return the unrevoked tokens, expired ones included, at most one per subject
     */
    List<ApiToken> findLive(ApiTokenKind kind, Collection<UUID> subjectIds);
}
