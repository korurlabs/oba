package com.korur.oba.apps.kaya.core.security.helper;

// 1. Framework-level
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// 2. Third-party
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.constant.SecurityLogConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;

/**
 * Resolves the Kaya user of the request's Keycloak JWT, once per request.
 *
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} stores the user on the
 * request once its checks pass, and this provider returns that stored user. Only where no
 * organization check ran, as on a path of
 * {@link SecurityConstant#ORGANIZATION_EXEMPT_PATHS}, does it load the user by the JWT's sub claim and
 * store it for the rest of the request.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CurrentUserProvider.class);

    private final KayaUserQueryService kayaUserQueryService;
    private final LoggingService loggingService;

    /**
     * Returns the user of the request's JWT.
     *
     * @param request the current request
     * @return the user, or empty when the request carries no JWT authentication
     * @throws UnauthorizedException when the JWT has no sub claim
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException when no user
     *         is provisioned for the JWT's subject yet
     */
    public Optional<User> findCurrentUser(HttpServletRequest request) {
        Object cached = request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER);
        if (cached instanceof User cachedUser) {
            return Optional.of(cachedUser);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            loggingService.debug(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.NO_JWT_AUTHENTICATION);
            return Optional.empty();
        }

        String keycloakId = jwtAuthentication.getToken().getSubject();
        if (keycloakId == null || keycloakId.isBlank()) {
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.JWT_SUBJECT_MISSING);
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }

        User user = kayaUserQueryService.getEntityByKeycloakId(keycloakId);
        request.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER, user);
        loggingService.debug(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.RESOLVED_CURRENT_USER, user.getId(), keycloakId);
        return Optional.of(user);
    }

    /**
     * Stores an already loaded user for the rest of the request.
     *
     * @param request the current request
     * @param user    the user of the request's JWT
     */
    public void cacheCurrentUser(HttpServletRequest request, User user) {
        request.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER, user);
    }
}
