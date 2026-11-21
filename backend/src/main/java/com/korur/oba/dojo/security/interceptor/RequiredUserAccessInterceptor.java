package com.korur.oba.dojo.security.interceptor;

// 1. Framework-level
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

// 2. Third-party
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.constant.SecurityLogConstant;
import com.korur.oba.dojo.security.token.authentication.ApiTokenAuthentication;
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.dojo.security.token.helper.ApiTokenKindRegistry;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.validator.UserAuthorizationValidator;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;

/**
 * Enforces the organization access of every controller method called with a Keycloak JWT or an API
 * token.
 *
 * Reads {@link RequiredUserAccess} off the handler method and runs the access checks in this
 * order. Steps 1 to 6 and 8 are {@link UserAuthorizationValidator} calls that log and throw their own error:
 * 0. An unannotated method passes only when its path is listed in
 *    {@link SecurityConstant#ORGANIZATION_EXEMPT_PATHS} or {@link SecurityConstant#PUBLIC_PATHS}, or, for
 *    an API token, when its kind lists the method and path among its exempt endpoints; the two path
 *    lists do not apply to tokens (403).
 * 1. A user is provisioned for the JWT subject, or the token's user exists (403).
 * 2. The user is ACTIVE (403).
 * 3. The organization id of the path variable or the X-Organization-ID header is present, a UUID,
 *    and the same in both when both are sent (400). A token acts in its own organization: without an id
 *    the token's is used, and another one is refused (403).
 * 4. The user is a member of that organization; an unknown organization is refused the same way (403).
 * 5. The membership is ACTIVE (403).
 * 6. The organization is ACTIVE, checked after the membership so only a member learns its status (403).
 * 7. The membership role is at least the annotated one (403).
 * 8. The member may open the app that the handler package names, the segment after com.korur.oba.apps;
 *    a handler outside the apps has no app check (403).
 * 9. The user and the membership are stored on the request for GetUser and the services.
 *
 * Default behavior: DEFAULT-DENY. Any other authentication is refused (403), except an anonymous one on
 * {@link SecurityConstant#PUBLIC_PATHS} and the authentication of an integration chain on
 * {@link SecurityConstant#INTEGRATION_CHAIN_PATHS}, which that chain guards. Handlers outside
 * com.korur.oba (SpringDoc, the error page) pass untouched.
 *
 * @see RequiredUserAccess
 * @see SecurityConstant#ORGANIZATION_EXEMPT_PATHS
 * @see ApiTokenKindRegistry#isExemptEndpoint
 */
@Component
@RequiredArgsConstructor
public class RequiredUserAccessInterceptor implements HandlerInterceptor {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RequiredUserAccessInterceptor.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final UserAuthorizationValidator userAuthorizationValidator;
    private final ApiTokenKindRegistry apiTokenKindRegistry;
    private final LoggingService loggingService;

    /**
     * Runs the access checks of the handler method before its arguments are resolved.
     *
     * @param request  the current request
     * @param response the current response
     * @param handler  the handler chosen for the request
     * @return true when the request may reach the handler; a refusal throws instead
     * @throws ForbiddenException  when the authentication is not supported on the path, the handler
     *                             declares no access rule, the user is not provisioned, missing or not
     *                             ACTIVE, the token names another organization, the membership is
     *                             missing or not ACTIVE, the organization is disabled, the role is too
     *                             low, or the member may not open the app of the handler
     * @throws BadRequestException when the organization id is missing, not a UUID, or differs
     *                             between the path and the header
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !handlerMethod.getBeanType().getPackageName().startsWith(SecurityConstant.OBA_PACKAGE_PREFIX)) {
            return true;
        }

        // getRequestURI() includes the servlet context path; strip it so the lists match the same
        // context-relative path that the request mappings use.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtAuthenticationToken jwtAuthentication = authentication instanceof JwtAuthenticationToken jwt ? jwt : null;
        ApiTokenAuthentication tokenAuthentication =
                authentication instanceof ApiTokenAuthentication token ? token : null;

        // Only a JWT and an API token reach the checks below. Anything else passes only where its own
        // chain answers for it, so a new credential type is closed until it is handled here.
        if (jwtAuthentication == null && tokenAuthentication == null) {
            boolean anonymous = authentication == null || authentication instanceof AnonymousAuthenticationToken;
            String[] allowedPaths = anonymous ? SecurityConstant.PUBLIC_PATHS : SecurityConstant.INTEGRATION_CHAIN_PATHS;
            if (Arrays.stream(allowedPaths).anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
                return true;
            }
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER, SecurityLogConstant.ACCESS_DENIED_AUTHENTICATION_UNSUPPORTED,
                    handlerMethod, path, authentication == null ? null : authentication.getClass().getName());
            throw new ForbiddenException(ExceptionSecurityError.FORBIDDEN);
        }

        RequiredUserAccess requiredUserAccess = handlerMethod.getMethodAnnotation(RequiredUserAccess.class);

        // 0. A forgotten annotation must close the endpoint, not open it.
        if (requiredUserAccess == null && tokenAuthentication != null) {
            if (!apiTokenKindRegistry.isExemptEndpoint(tokenAuthentication.getKind(), request.getMethod(), path)) {
                loggingService.error(LOG_PREFIX, LogLayer.HANDLER, SecurityLogConstant.ACCESS_DENIED_UNANNOTATED_FOR_TOKEN,
                        handlerMethod, request.getMethod(), path, tokenAuthentication.getKind());
                throw new ForbiddenException(ExceptionSecurityError.HANDLER_UNANNOTATED);
            }
            return true;
        }
        if (requiredUserAccess == null) {
            boolean listed = Stream.of(SecurityConstant.ORGANIZATION_EXEMPT_PATHS, SecurityConstant.PUBLIC_PATHS)
                    .flatMap(Arrays::stream)
                    .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
            if (!listed) {
                loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                        SecurityLogConstant.ACCESS_DENIED_UNANNOTATED, handlerMethod, path);
                throw new ForbiddenException(ExceptionSecurityError.HANDLER_UNANNOTATED);
            }
            return true;
        }

        Map<String, String> pathVariables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String pathOrganizationId = pathVariables == null
                ? null
                : pathVariables.get(SecurityConstant.PATH_VARIABLE_ORGANIZATION_ID);
        String headerOrganizationId = request.getHeader(CommonConstant.HEADER_ORGANIZATION_ID);

        // An SSE response is dispatched a second time within the same request. The first dispatch
        // passed every check and stored the principal, so it is reused for the same organization.
        ApiTokenPrincipal tokenPrincipal = tokenAuthentication == null ? null : tokenAuthentication.getPrincipal();
        if (request.getAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER)
                        instanceof OrganizationMember storedMember
                && storedMember.getOrganization().getId().equals(tokenPrincipal == null
                        ? userAuthorizationValidator.ensureOrganizationIdIsValid(
                                pathOrganizationId, headerOrganizationId, path)
                        : userAuthorizationValidator.ensureApiTokenOrganizationId(
                                pathOrganizationId, headerOrganizationId, tokenPrincipal.organizationId(), path))) {
            return true;
        }

        // 1. The JWT subject must belong to a provisioned user; a token's user must still exist.
        User user = tokenPrincipal == null
                ? userAuthorizationValidator.ensureUserIsProvisioned(jwtAuthentication.getToken().getSubject(), path)
                : userAuthorizationValidator.ensureUserExistsById(tokenPrincipal.userId(), path);

        // 2. A disabled or onboarding account has no organization access.
        userAuthorizationValidator.ensureUserIsActive(user, path);

        // 3. One organization id, from the path or the header, never two different ones; a token acts
        // only in its own organization.
        UUID organizationId = tokenPrincipal == null
                ? userAuthorizationValidator.ensureOrganizationIdIsValid(pathOrganizationId, headerOrganizationId, path)
                : userAuthorizationValidator.ensureApiTokenOrganizationId(
                        pathOrganizationId, headerOrganizationId, tokenPrincipal.organizationId(), path);

        // 4. Membership before anything about the organization, so an unknown organization and a
        // foreign one look the same.
        OrganizationMember member = userAuthorizationValidator.ensureUserIsMember(user.getId(), organizationId, path);

        // 5. A disabled membership keeps its row but has lost access.
        userAuthorizationValidator.ensureMembershipIsActive(member, path);

        // 6. Only a member gets to learn that the organization is disabled.
        userAuthorizationValidator.ensureOrganizationIsActive(member, path);

        // 7. The role of this organization's membership, never the highest role across organizations.
        OrganizationMemberRole requiredRole = requiredUserAccess.value();
        if (!member.getRole().isAtLeast(requiredRole)) {
            loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, SecurityLogConstant.USER_ROLE_DENIED,
                    user.getId(), member.getRole(), requiredRole, path);
            throw new ForbiddenException(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
        }

        // 8. After the role, so a member below the handler's role is refused for the role, not the app.
        userAuthorizationValidator.ensureAppAccess(member, handlerMethod.getBeanType().getPackageName(), path);

        // 9. Stored only now, so a refused request never carries a principal.
        request.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_CURRENT_USER, user);
        request.setAttribute(SecurityConstant.REQUEST_ATTRIBUTE_ORGANIZATION_MEMBER, member);
        loggingService.debug(LOG_PREFIX, LogLayer.HANDLER, SecurityLogConstant.ROLE_CHECK_PASSED,
                user.getId(), member.getRole(), requiredRole);
        return true;
    }
}
