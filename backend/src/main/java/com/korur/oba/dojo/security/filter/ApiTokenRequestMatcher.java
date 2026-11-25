package com.korur.oba.dojo.security.filter;

// 1. Framework-level
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;

// 2. Third-party
import jakarta.servlet.http.HttpServletRequest;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.helper.RequestPathHelper;
import com.korur.oba.dojo.security.token.helper.ApiTokenKindRegistry;

/**
 * Selects the requests of the API token chain: a bearer value with the prefix of an accepted kind, on
 * any path, and any request on a path a kind reserves, whatever its credential, so a JWT sent there is
 * refused by the token filter instead of being accepted by the JWT chain.
 *
 * The path is the one of {@link RequestPathHelper}, the same path the MVC routing matches, so an encoded
 * form of a reserved path is selected too.
 */
public class ApiTokenRequestMatcher implements RequestMatcher {

    private final ApiTokenKindRegistry apiTokenKindRegistry;

    public ApiTokenRequestMatcher(ApiTokenKindRegistry apiTokenKindRegistry) {
        this.apiTokenKindRegistry = apiTokenKindRegistry;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return apiTokenKindRegistry.findByToken(bearerToken(request)).isPresent()
                || apiTokenKindRegistry.findByReservedPath(RequestPathHelper.pathWithinApplication(request)).isPresent();
    }

    /**
     * Reads the bearer value of the Authorization header.
     *
     * @param request the request
     * @return the value after "Bearer ", trimmed, or null when the header is missing or of another scheme
     */
    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(SecurityConstant.AUTHORIZATION_BEARER_PREFIX)) {
            return null;
        }
        return header.substring(SecurityConstant.AUTHORIZATION_BEARER_PREFIX.length()).trim();
    }
}
