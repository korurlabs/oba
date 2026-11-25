package com.korur.oba.dojo.security.token.helper;

// 1. Framework-level
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.security.token.constant.ApiTokenConstant;
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.handler.ApiTokenKindHandler;

/**
 * The token kinds the application accepts: one {@link ApiTokenKindHandler} bean per kind. A kind
 * whose handler is missing is unknown to the token chain, so its tokens fall through to the JWT chain
 * and fail there.
 *
 * Built once at startup; two handlers for one kind stop the application, because the chain could not
 * tell which rules apply.
 */
@Component
public class ApiTokenKindRegistry {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Map<ApiTokenKind, ApiTokenKindHandler> handlers = new EnumMap<>(ApiTokenKind.class);

    /**
     * @param handlerProvider every kind handler bean; none is a valid set
     * @throws IllegalStateException when two handlers name the same kind
     */
    public ApiTokenKindRegistry(ObjectProvider<ApiTokenKindHandler> handlerProvider) {
        for (ApiTokenKindHandler handler : handlerProvider) {
            ApiTokenKindHandler previous = handlers.get(handler.kind());
            if (previous != null) {
                throw new IllegalStateException(ApiTokenConstant.DUPLICATE_KIND_HANDLER
                        + handler.kind() + ": " + previous.getClass().getName() + ", "
                        + handler.getClass().getName());
            }
            handlers.put(handler.kind(), handler);
        }
    }

    /**
     * @return the handlers of every accepted kind
     */
    public Collection<ApiTokenKindHandler> handlers() {
        return handlers.values();
    }

    /**
     * @param kind a token kind
     * @return its handler, or empty when the kind is not accepted
     */
    public Optional<ApiTokenKindHandler> findByKind(ApiTokenKind kind) {
        return Optional.ofNullable(handlers.get(kind));
    }

    /**
     * Finds the handler of the kind a bearer value claims by its prefix.
     *
     * @param bearerToken the bearer value without "Bearer ", possibly null
     * @return the handler, or empty when the value carries no prefix of an accepted kind
     */
    public Optional<ApiTokenKindHandler> findByToken(String bearerToken) {
        Optional<ApiTokenKind> kind = ApiTokenKind.fromToken(bearerToken);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        return findByKind(kind.get());
    }

    /**
     * Finds the kind that reserves a path for itself.
     *
     * @param path the decoded, context-relative request path
     * @return the handler of the kind, or empty when no kind reserves the path
     */
    public Optional<ApiTokenKindHandler> findByReservedPath(String path) {
        for (ApiTokenKindHandler handler : handlers.values()) {
            for (String pattern : handler.reservedPaths()) {
                if (PATH_MATCHER.match(pattern, path)) {
                    return Optional.of(handler);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a kind may call an unannotated handler.
     *
     * @param kind   the kind of the request's token
     * @param method the HTTP method of the request
     * @param path   the context-relative request path
     * @return true when the kind lists the method and path among its exempt endpoints
     */
    public boolean isExemptEndpoint(ApiTokenKind kind, String method, String path) {
        ApiTokenKindHandler handler = handlers.get(kind);
        if (handler == null) {
            return false;
        }
        for (ApiTokenEndpoint endpoint : handler.exemptEndpoints()) {
            if (endpoint.method().matches(method) && PATH_MATCHER.match(endpoint.pathPattern(), path)) {
                return true;
            }
        }
        return false;
    }
}
