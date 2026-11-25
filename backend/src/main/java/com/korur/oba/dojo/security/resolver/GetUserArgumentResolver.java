package com.korur.oba.dojo.security.resolver;

// 1. Framework-level
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;

import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.constant.SecurityLogConstant;
import com.korur.oba.dojo.security.token.authentication.ApiTokenAuthentication;
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.constant.UserExceptionLogConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.repository.UserRepository;

/**
 * Resolves method parameters annotated with {@link GetUser} by extracting the
 * authenticated user from the SecurityContext and fetching the corresponding
 * User entity from the database.
 *
 * <p>This resolver implements request-scoped caching to avoid duplicate database
 * queries when multiple {@code @GetUser} annotations are used in the same request. The user
 * that {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} stores
 * under {@link SecurityConstant#REQUEST_ATTRIBUTE_CURRENT_USER} is reused the same way.</p>
 *
 * <p><b>Resolution flow:</b></p>
 * <ol>
 *   <li>Check request-scoped cache for previously resolved user</li>
 *   <li>Extract Authentication from SecurityContextHolder</li>
 *   <li>Extract Keycloak user ID from JWT "sub" claim</li>
 *   <li>Query database for User entity by Keycloak ID</li>
 *   <li>Validate user status if {@code requireActive = true}</li>
 *   <li>Cache user in request scope</li>
 *   <li>Return user or throw appropriate exception</li>
 * </ol>
 *
 * @see GetUser
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class GetUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(GetUserArgumentResolver.class);

    private final UserRepository userRepository;
    private final LoggingService loggingService;

    /**
     * Determines if this resolver supports the given method parameter.
     *
     * <p>Returns {@code true} if the parameter is annotated with {@link GetUser}
     * and the parameter type is assignable to {@link User}.</p>
     *
     * @param parameter the method parameter to check
     * @return {@code true} if this resolver can resolve the parameter
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(GetUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * Resolves the User parameter by extracting authentication and fetching from database.
     *
     * <p>This method implements request-scoped caching to optimize performance when
     * multiple {@code @GetUser} annotations are used in the same request.</p>
     *
     * @param parameter the method parameter to resolve
     * @param mavContainer the model and view container (not used)
     * @param webRequest the native web request for accessing request attributes
     * @param binderFactory the data binder factory (not used)
     * @return the resolved User entity, or {@code null} if optional and not authenticated
     * @throws UnauthorizedException if authentication is missing and user is not optional
     * @throws NotFoundException if user is not found in database
     * @throws ForbiddenException if user status is not ACTIVE and requireActive is true
     */
    @Override
    public User resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        GetUser getUserAnnotation = parameter.getParameterAnnotation(GetUser.class);
        if (getUserAnnotation == null) {
            loggingService.error(LOG_PREFIX, LogLayer.FILTER,
                    "GetUser annotation not found on parameter, but supportsParameter returned true");
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }

        boolean isOptional = getUserAnnotation.optional();
        boolean requireActive = getUserAnnotation.requireActive();

        // Check request-scoped cache first to avoid duplicate DB queries
        User cachedUser = (User) webRequest.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER, RequestAttributes.SCOPE_REQUEST);
        if (cachedUser != null) {
            loggingService.debug(LOG_PREFIX, LogLayer.FILTER,
                    "Returning cached user for keycloakId: {}", cachedUser.getKeycloakId());

            // Still validate status for cached user if required
            if (requireActive && cachedUser.getStatus() != UserStatus.ACTIVE) {
                loggingService.warn(LOG_PREFIX, LogLayer.FILTER,
                        "Cached user status is not ACTIVE: keycloakId={}, status={}",
                        cachedUser.getKeycloakId(), cachedUser.getStatus());
                throw new ForbiddenException(
                    ExceptionSecurityError.FORBIDDEN,
                    Map.of("status", cachedUser.getStatus().name())
                );
            }

            return cachedUser;
        }

        // Extract authentication from SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // A token caller has no Keycloak subject; the token names its user directly.
        if (authentication instanceof ApiTokenAuthentication tokenAuthentication) {
            return resolveApiTokenUser(tokenAuthentication, requireActive, webRequest);
        }

        if (authentication == null || !(authentication instanceof JwtAuthenticationToken)) {
            loggingService.debug(LOG_PREFIX, LogLayer.FILTER,
                    "No valid JWT authentication found in SecurityContext");

            if (isOptional) {
                loggingService.debug(LOG_PREFIX, LogLayer.FILTER,
                        "User is optional, returning null");
                return null;
            }

            loggingService.warn(LOG_PREFIX, LogLayer.FILTER,
                    "User is required but no authentication found");
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }

        // Extract JWT and Keycloak ID from token
        JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
        Jwt jwt = jwtToken.getToken();
        String keycloakId = jwt.getSubject(); // "sub" claim contains Keycloak user ID

        User user;
        if (keycloakId != null && !keycloakId.isBlank()) {
            loggingService.debug(LOG_PREFIX, LogLayer.FILTER,
                    "Resolving user from database for keycloakId: {}", keycloakId);
            user = userRepository.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> {
                        loggingService.error(LOG_PREFIX, LogLayer.FILTER,
                                "User not found in database for keycloakId: {}", keycloakId);
                        return new NotFoundException(
                            ExceptionDomainIdentityError.NOT_USER_FOUND,
                            Map.of(UserConstant.FIELD_USER_KEYCLOAK_ID, keycloakId)
                        );
                    });
        } else {
            // Fallback: resolve by email when sub claim is missing
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                loggingService.error(LOG_PREFIX, LogLayer.FILTER,
                        "JWT token has no 'sub' or 'email' claim");
                throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
            }
            loggingService.debug(LOG_PREFIX, LogLayer.FILTER,
                    "Resolving user from database by email: {}", email);
            user = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> {
                        loggingService.error(LOG_PREFIX, LogLayer.FILTER,
                                "User not found in database for email: {}", email);
                        return new NotFoundException(
                            ExceptionDomainIdentityError.NOT_USER_FOUND,
                            Map.of("email", email)
                        );
                    });
        }

        loggingService.debug(LOG_PREFIX, LogLayer.FILTER,
                "User found in database: id={}, email={}, status={}",
                user.getId(), user.getEmail(), user.getStatus());

        // Validate user status if required
        if (requireActive && user.getStatus() != UserStatus.ACTIVE) {
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER,
                    "User status is not ACTIVE: keycloakId={}, status={}", keycloakId, user.getStatus());
            throw new ForbiddenException(
                ExceptionSecurityError.FORBIDDEN,
                Map.of("status", user.getStatus().name())
            );
        }

        // Cache user in request scope for subsequent @GetUser annotations
        webRequest.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER, user, RequestAttributes.SCOPE_REQUEST);
        loggingService.debug(LOG_PREFIX, LogLayer.FILTER,
                "User cached in request scope for keycloakId: {}", keycloakId);

        return user;
    }

    /**
     * Resolves the user an API token acts as, by the user id of its principal.
     *
     * Reached on an endpoint the token kind may call without RequiredUserAccess, such as GET /users/me;
     * an annotated endpoint has already stored the user on the request.
     *
     * @param tokenAuthentication the authentication set by the API token chain
     * @param requireActive       whether a user that is not ACTIVE is refused
     * @param webRequest          the request, which caches the user
     * @return the user
     * @throws NotFoundException  if the user no longer exists
     * @throws ForbiddenException if the user is not ACTIVE and requireActive is true
     */
    private User resolveApiTokenUser(
            ApiTokenAuthentication tokenAuthentication, boolean requireActive, NativeWebRequest webRequest) {
        ApiTokenPrincipal principal = tokenAuthentication.getPrincipal();
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.API_TOKEN_USER_NOT_FOUND,
                            principal.userId(), tokenAuthentication.getKind(), principal.tokenId());
                    return new NotFoundException(
                            ExceptionDomainIdentityError.NOT_USER_FOUND,
                            Map.of(UserConstant.FIELD_USER_DATABASE_ID, principal.userId()));
                });

        if (requireActive && user.getStatus() != UserStatus.ACTIVE) {
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, UserExceptionLogConstant.USER_STATUS_NOT_ACTIVE,
                    user.getId());
            throw new ForbiddenException(
                    ExceptionSecurityError.FORBIDDEN,
                    Map.of(UserConstant.FIELD_USER_STATUS, user.getStatus().name()));
        }

        webRequest.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER, user, RequestAttributes.SCOPE_REQUEST);
        loggingService.debug(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.RESOLVED_API_TOKEN_USER,
                user.getId(), tokenAuthentication.getKind(), principal.tokenId());
        return user;
    }
}
