package com.korur.oba.shrine.identity.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.UuidUtil;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.constant.UserExceptionLogConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Access checks that decide whether the user of a request may act in an organization.
 *
 * {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} calls one method per
 * step, in a fixed order, so each check logs and throws its own error. Every refusal is a 403, except
 * {@link #ensureOrganizationIdIsValid}, which answers 400 for a request that names no organization,
 * a malformed one, or two different ones.
 *
 * The lifecycle checks of {@link UserValidator} and
 * {@link com.korur.oba.shrine.organization.validator.OrganizationValidator} answer 400 and 409 to
 * business callers and are not used here: a refused request and a lifecycle conflict are different
 * answers.
 */
@Component
@RequiredArgsConstructor
public class UserAuthorizationValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserAuthorizationValidator.class);

    private final UserRepository userRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final LoggingService loggingService;

    /**
     * Ensures a user is provisioned for the JWT subject and returns it.
     *
     * @param keycloakId  the Keycloak user id from the JWT sub claim
     * @param requestPath the request path, for the log line
     * @return the provisioned user
     * @throws ForbiddenException if no user has this Keycloak id
     */
    public User ensureUserIsProvisioned(String keycloakId, String requestPath) {
        Optional<User> user = keycloakId == null || keycloakId.isBlank()
                ? Optional.empty()
                : userRepository.findByKeycloakId(keycloakId);
        if (user.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.ACCESS_DENIED_NOT_PROVISIONED, requestPath, keycloakId);
            throw new ForbiddenException(ExceptionSecurityError.FORBIDDEN);
        }
        return user.get();
    }

    /**
     * Ensures the user is ACTIVE; a disabled or onboarding account has no organization access.
     *
     * @param user        the user of the request
     * @param requestPath the request path, for the log line
     * @throws ForbiddenException if the user is not ACTIVE
     */
    public void ensureUserIsActive(User user, String requestPath) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.ACCESS_DENIED_USER_NOT_ACTIVE, requestPath, user.getId(), user.getStatus());
            throw new ForbiddenException(ExceptionSecurityError.FORBIDDEN,
                    Map.of(UserConstant.FIELD_USER_STATUS, user.getStatus().name()));
        }
    }

    /**
     * Ensures the request names one organization and returns its id.
     *
     * The organizationId path variable is read first and the X-Organization-ID header second. When
     * both are sent they must be equal, so a request cannot pass the check for one organization and
     * act on another.
     *
     * @param pathOrganizationId   the raw organizationId path variable, or null when the path has none
     * @param headerOrganizationId the raw X-Organization-ID header, or null when it was not sent
     * @param requestPath          the request path, for the log line
     * @return the organization id of the request
     * @throws BadRequestException if neither is sent, either is not a UUID, or the two differ
     */
    public UUID ensureOrganizationIdIsValid(
            String pathOrganizationId, String headerOrganizationId, String requestPath) {
        boolean pathPresent = pathOrganizationId != null && !pathOrganizationId.isBlank();
        boolean headerPresent = headerOrganizationId != null && !headerOrganizationId.isBlank();
        if (!pathPresent && !headerPresent) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.ACCESS_DENIED_ORGANIZATION_ID_MISSING, requestPath);
            throw new BadRequestException(ExceptionApiError.MISSING_HEADER,
                    Map.of(UserConstant.FIELD_HEADER, CommonConstant.HEADER_ORGANIZATION_ID));
        }

        UUID pathId = null;
        if (pathPresent) {
            Optional<UUID> parsedPath = UuidUtil.parse(pathOrganizationId);
            if (parsedPath.isEmpty()) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                        UserExceptionLogConstant.ACCESS_DENIED_PATH_VARIABLE_INVALID, requestPath);
                throw new BadRequestException(ExceptionApiError.BAD_REQUEST);
            }
            pathId = parsedPath.get();
        }

        if (!headerPresent) {
            return pathId;
        }
        Optional<UUID> parsedHeader = UuidUtil.parse(headerOrganizationId);
        if (parsedHeader.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.ACCESS_DENIED_HEADER_INVALID, requestPath);
            throw new BadRequestException(ExceptionDomainOrganizationError.API_INVALID_ORGANIZATION_ID);
        }
        UUID headerId = parsedHeader.get();
        if (pathId != null && !pathId.equals(headerId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.ACCESS_DENIED_ORGANIZATION_ID_MISMATCH, requestPath, pathId);
            throw new BadRequestException(ExceptionDomainOrganizationError.API_ORGANIZATION_ID_MISMATCH,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, pathId));
        }
        return headerId;
    }

    /**
     * Ensures the user is a member of the organization and returns the membership.
     *
     * An organization that does not exist has no members, so it is refused with the same code as a
     * foreign one and a caller cannot probe which organization ids exist.
     *
     * @param userId         the id of the user of the request
     * @param organizationId the organization id from {@link #ensureOrganizationIdIsValid}
     * @param requestPath    the request path, for the log line
     * @return the membership, for the later steps
     * @throws ForbiddenException if the user has no membership in the organization
     */
    public OrganizationMember ensureUserIsMember(UUID userId, UUID organizationId, String requestPath) {
        Optional<OrganizationMember> member = organizationMemberQueryService.findMember(organizationId, userId);
        if (member.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.ACCESS_DENIED_NOT_MEMBER, requestPath, userId, organizationId);
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_USER_NOT_MEMBER,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
        }
        return member.get();
    }

    /**
     * Ensures the membership is ACTIVE; a disabled membership keeps its row but has lost access.
     *
     * Refused with the not-member code, so a removed or disabled member learns nothing more than
     * any other caller without access.
     *
     * @param member      the membership from {@link #ensureUserIsMember}
     * @param requestPath the request path, for the log line
     * @throws ForbiddenException if the membership is disabled
     */
    public void ensureMembershipIsActive(OrganizationMember member, String requestPath) {
        if (member.getStatus() != OrganizationMemberStatus.ACTIVE) {
            UUID organizationId = member.getOrganization().getId();
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.ACCESS_DENIED_MEMBERSHIP_DISABLED,
                    requestPath, member.getUser().getId(), organizationId, member.getStatus());
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_USER_NOT_MEMBER,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
        }
    }

    /**
     * Ensures the organization of the membership is ACTIVE.
     *
     * Takes the membership, not the organization, so only a member can learn that an organization
     * is disabled.
     *
     * @param member      the membership from {@link #ensureUserIsMember}
     * @param requestPath the request path, for the log line
     * @throws ForbiddenException if the organization is disabled
     */
    public void ensureOrganizationIsActive(OrganizationMember member, String requestPath) {
        Organization organization = member.getOrganization();
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.ACCESS_DENIED_ORGANIZATION_DISABLED,
                    requestPath, member.getUser().getId(), organization.getId(), organization.getStatus());
            throw new ForbiddenException(
                    ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organization.getId()));
        }
    }

    /**
     * Ensures the member may open the app of the request handler.
     *
     * The app is the package segment after com.korur.oba.apps of the handler, so a handler outside
     * the apps (shrine, connect, dojo) has no app and passes. An apps package without an {@link App}
     * constant is refused, so a new app stays closed until it has a constant and grants.
     *
     * Whether the member may open the app is decided by
     * {@link AppAccessQueryService#hasAppAccess(OrganizationMember, App)}, which also refuses a member
     * below the minimum role of the app: a GUEST is refused {@link App#KAYA} even when a grant names the
     * GUEST. Call it after the role check, so a member below the role of the handler is refused for the role.
     *
     * @param member         the ACTIVE membership of an ACTIVE organization from the earlier steps
     * @param handlerPackage the package name of the handler bean type
     * @param requestPath    the request path, for the log line
     * @throws ForbiddenException APP_SEC_001 if the package names no known app, or the member may
     *                            not open the app
     */
    public void ensureAppAccess(OrganizationMember member, String handlerPackage, String requestPath) {
        if (handlerPackage == null || !handlerPackage.startsWith(AccessConstant.APP_PACKAGE_PREFIX)) {
            return;
        }
        UUID organizationId = member.getOrganization().getId();
        String appSegment = appSegmentOf(handlerPackage);
        Optional<App> app = App.fromAppPackage(appSegment);
        if (app.isEmpty()) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR, UserExceptionLogConstant.ACCESS_DENIED_APP_UNKNOWN,
                    requestPath, handlerPackage, member.getUser().getId(), organizationId);
            throw new ForbiddenException(ExceptionDomainAccessError.SEC_APP_ACCESS_DENIED,
                    Map.of(OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
        }

        App requestedApp = app.get();
        if (!appAccessQueryService.hasAppAccess(member, requestedApp)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, UserExceptionLogConstant.ACCESS_DENIED_NO_APP_ACCESS,
                    requestPath, member.getUser().getId(), organizationId, requestedApp, member.getRole());
            throw new ForbiddenException(ExceptionDomainAccessError.SEC_APP_ACCESS_DENIED,
                    Map.of(
                            OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId,
                            AccessConstant.FIELD_APP, requestedApp.name()));
        }
    }

    /**
     * Returns the package segment that names the app, such as kaizen for com.korur.oba.apps.kaizen.board.
     */
    private static String appSegmentOf(String handlerPackage) {
        String rest = handlerPackage.substring(AccessConstant.APP_PACKAGE_PREFIX.length());
        int end = rest.indexOf('.');
        return end < 0 ? rest : rest.substring(0, end);
    }
}
