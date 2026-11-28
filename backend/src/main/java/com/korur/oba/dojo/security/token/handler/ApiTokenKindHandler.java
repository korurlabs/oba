package com.korur.oba.dojo.security.token.handler;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;
import com.korur.oba.dojo.security.token.dto.ApiTokenBodyLimit;
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.dojo.security.token.dto.ApiTokenRefusedPaths;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

/**
 * What one token kind adds to the shared token mechanism. Each kind implements it as a bean in this
 * package, so every credential rule of the application lives in the security layer, and reads its subject
 * through the subject owner's query service; a kind without a bean is not accepted at all.
 *
 * The token chain authenticates a request with the kind's token, refuses it with the kind's error,
 * refuses it with 403 on the kind's refused paths, applies the body limit and sets the principal. The
 * request access check then applies the handler's RequiredUserAccess to the principal's user and
 * organization, and lets the kind call only the unannotated handlers it lists.
 */
public interface ApiTokenKindHandler {

    /**
     * @return the kind this handler stands for; one handler per kind
     */
    ApiTokenKind kind();

    /**
     * Paths only this kind may call. Any other credential sent there, a JWT or a token of another kind,
     * is refused with {@link #invalidTokenError()}, and a token of this kind is refused on every other
     * path, so the kind's credential opens nothing beyond them.
     *
     * @return Spring path patterns, empty when the kind may call any path its role allows
     */
    List<String> reservedPaths();

    /**
     * Paths a token of this kind is refused on with 403, although its role and app grants would allow
     * them. The token is authenticated first, so an unknown or revoked one still answers
     * {@link #invalidTokenError()}. A path another kind reserves is refused with that kind's 401 before.
     *
     * @return the refused paths with their error, or empty when the role and app grants alone decide
     */
    Optional<ApiTokenRefusedPaths> refusedPaths();

    /**
     * @return the handlers without RequiredUserAccess this kind may call, matched by method and path
     */
    List<ApiTokenEndpoint> exemptEndpoints();

    /**
     * @return the largest body this kind may send, or empty for no limit beyond the server's
     */
    Optional<ApiTokenBodyLimit> bodyLimit();

    /**
     * Builds the caller of an authenticated token after checking that its subject may still act.
     *
     * @param token a live token of this kind
     * @return the principal of the request
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException when the
     *         subject may no longer act, such as a retired machine; the chain answers it with
     *         {@link #invalidTokenError()}
     */
    ApiTokenPrincipal resolvePrincipal(ApiToken token);

    /**
     * @return the 401 error of this kind, answered for every refusal so a caller cannot tell an unknown
     *         token from a revoked one
     */
    ExceptionDomainError invalidTokenError();
}
