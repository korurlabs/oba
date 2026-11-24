package com.korur.oba.shrine.identity.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.audit.mapper.AuditLogMapper;
import com.korur.oba.dojo.audit.service.AuditService;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.constant.service.UserServiceLogConstant;
import com.korur.oba.shrine.identity.dto.request.UserSelfUpdateRequest;
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;
import com.korur.oba.shrine.identity.dto.response.UserSyncResult;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.mapper.UserMapper;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.service.UserProfileService;
import com.korur.oba.shrine.identity.validator.UserValidator;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.event.AccessRevokedEvent;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

/**
 * Purpose:
 * Handles JWT-based user synchronization -- creating or linking local user
 * records from Keycloak JWT tokens.
 *
 * Bigger picture:
 * Acts as the write side of the user profile CQRS split. When a user
 * authenticates via Keycloak for the first time, this service creates their
 * local user record. On subsequent logins, it links or retrieves the existing
 * record. Consumed by UserProfileController to serve the POST /users/me
 * endpoint.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-04-09
 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserProfileServiceImpl.class);

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final UserMapper userMapper;
    private final OrganizationQueryService organizationQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    /**
     * Syncs the authenticated Keycloak user into the local database.
     *
     * Step 1 - Extract JWT claims:
     * Retrieves the JWT from the SecurityContext and extracts the keycloakId
     * (subject) and email claims needed for user identification.
     *
     * Step 2 - Validate email:
     * Ensures the JWT contains a non-blank email claim. Without an email,
     * the user cannot be created or identified in the system.
     *
     * Step 3 - Find by keycloakId:
     * Attempts to locate an existing user by their Keycloak subject ID.
     * If found, returns immediately with created=false since the user
     * is already synced. If not found, refuses a subject that belongs to a
     * soft-deleted user, so that a deleted person cannot register again by
     * logging in with the Keycloak account that deletion left in place.
     *
     * Step 4 - Find by email and link keycloakId:
     * If no user was found by keycloakId, searches by email. This handles
     * the case where a user was created manually (e.g., by admin) before
     * their first Keycloak login. Links the keycloakId to the existing
     * user record for future lookups.
     *
     * Step 5 - Create new user:
     * If no existing user is found by either keycloakId or email, creates
     * a new user entity with PENDING_ONBOARDING status and persists it.
     * Returns created=true so the controller responds with 201.
     *
     * @docType GUIDE
     * @author Claude
     * @since 2026-03-04
     * @updated 2026-04-09
     * @return UserSyncResult containing the user detail response and a flag
     *         indicating whether the user was newly created
     * @throws ForbiddenException if the keycloakId belongs to a soft-deleted user
     */
    @Transactional
    @Override
    public UserSyncResult syncUserFromJwt() {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.SERVICE,
                UserServiceLogConstant.USER_JWT_CHECK_START);

        // 1. Extract JWT claims
        Jwt jwt = extractJwt();
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        // 2. Validate email
        if (email == null || email.isBlank()) {
            throw new ValidationException(ExceptionDomainIdentityError.NOT_USER_EMAIL_IS_MISSING, Map.of(
                UserConstant.FIELD_USER_KEYCLOAK_ID, keycloakId != null ? keycloakId : "unknown"));
        }

        // 3. Find by keycloakId
        if (keycloakId != null && !keycloakId.isBlank()) {
            Optional<User> existingUser = userRepository.findByKeycloakId(keycloakId);
            if (existingUser.isPresent()) {
                return new UserSyncResult(toDetailResponse(existingUser.get()), false);
            }
            userValidator.ensureKeycloakIdNotDeleted(keycloakId);
        }

        // 4. Find by email and link keycloakId if user was created before their first Keycloak login
        Optional<User> existingByEmail = userRepository.findByEmailIgnoreCase(email);
        if (existingByEmail.isPresent()) {
            User user = existingByEmail.get();
            if (keycloakId != null && !keycloakId.isBlank()
                    && (user.getKeycloakId() == null || user.getKeycloakId().isBlank())) {
                user.setKeycloakId(keycloakId);
                userRepository.save(user);
            }
            return new UserSyncResult(toDetailResponse(user), false);
        }

        // 5. Create new user
        userValidator.ensureMailIsUnique(email);

        User newUser = User.builder()
                .keycloakId(keycloakId)
                .email(email)
                .status(UserStatus.PENDING_ONBOARDING)
                .build();

        userRepository.save(newUser);

        loggingService.debug(
                LOG_PREFIX,
                LogLayer.SERVICE,
                UserServiceLogConstant.USER_CREATED_FROM_JWT,
                keycloakId);

        return new UserSyncResult(toDetailResponse(newUser), true);
    }

    @Transactional
    @Override
    public UserDetailResponse updateProfile(UUID userId, UserSelfUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND));

        // The email check runs before the mapping, so a taken email is refused before
        // the change reaches the persistence context.
        if (Objects.nonNull(request.email())) {
            userValidator.ensureNoOtherUserHasEmail(request.email(), userId);
        }

        userMapper.updateEntity(user, request);

        if (Objects.nonNull(request.defaultOrganizationId())) {
            userValidator.ensureUserIsMemberOfOrganization(userId, request.defaultOrganizationId());
            Organization organization = organizationQueryService
                    .getOrganizationEntityById(request.defaultOrganizationId());
            user.setDefaultOrganization(organization);
        }

        user = userRepository.save(user);

        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                UserServiceLogConstant.USER_UPDATED,
                user.getId());

        return toDetailResponse(user);
    }

    @Transactional
    @Override
    public void disableOwnAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND));

        userValidator.ensureUserStatusActive(user);
        // A disabled account still counts as an active owner but cannot sign in, so the
        // sole owner of an organization is refused to keep that organization manageable.
        organizationMemberQueryService.ensureNotSoleOwnerOfAnyOrganization(userId);

        Map<String, Object> oldValues = Map.of("status", user.getStatus().name());

        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        // A disabled account may no longer reach any organization, so its open realtime
        // sessions and unused handshake tickets end with this change, not when the token expires.
        applicationEventPublisher.publishEvent(AccessRevokedEvent.user(userId));

        auditService.log(auditLogMapper.toRequest(null, userId,
                UserServiceLogConstant.AUDIT_ACTION_USER_DISABLED,
                UserServiceLogConstant.AUDIT_ENTITY_TYPE, userId, oldValues,
                Map.of("status", UserStatus.DISABLED.name())));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                UserServiceLogConstant.USER_DISABLED, userId);
    }

    /**
     * Maps a user to the detail response, with the apps of every organization of the user read in one
     * step, so the organizations show the apps the user can open now.
     */
    private UserDetailResponse toDetailResponse(User user) {
        return userMapper.toDetailResponse(user, appAccessQueryService.listAccessibleAppsByOrganization(user));
    }

    /**
     * Purpose:
     * Extracts the JWT token from the current SecurityContext.
     *
     * Bigger picture:
     * Shared utility to obtain the authenticated JWT without duplicating
     * SecurityContext access logic across methods.
     *
     * @docType STANDARD
     * @author Claude
     * @since 2026-03-04
     * @updated 2026-03-04
     * @return the JWT token from the current authentication
     * @throws UnauthorizedException if no JWT authentication is present
     */
    private Jwt extractJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof JwtAuthenticationToken jwtToken)) {
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }
        return jwtToken.getToken();
    }
}
