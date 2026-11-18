package com.korur.oba.shrine.identity.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.enums.ExceptionStateError;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.constant.UserExceptionLogConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserValidator.class);

    private final UserRepository userRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final LoggingService loggingService;

    /**
     * Ensures that the given email address is not already used by any existing
     * user.
     *
     * Typical usage:
     * 1) Creation flows (new user registration / admin create user)
     * 2) Any scenario where the email must be globally unique
     *
     * Validation logic:
     * 1) Search for a user by email (case-insensitive).
     * 2) If a user exists, throw a {@link ConflictException} indicating a duplicate
     * unique value.
     *
     * @param email the email address to validate (case-insensitive)
     * @throws ConflictException if a user already exists with the given email
     */
    public void ensureMailIsUnique(String email) {
        Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(email);
        if (optionalUser.isPresent()) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.USER_EMAIL_ALREADY_EXISTS, email);
            throw new ConflictException(
                    ExceptionStateError.DUPLICATE_ENTITY,
                    Map.of(UserConstant.FIELD_USER_EMAIL, email));
        }
    }

    /**
     * Ensures that the given email address is not already used by a different user.
     *
     * This method is intended for update flows where the current user is allowed to
     * keep their existing email, but is not allowed to use an email belonging to
     * someone else.
     *
     * Validation logic:
     * 1) Search for a user by email (case-insensitive).
     * 2) If no user exists, the check passes.
     * 3) If a user exists and the found user's ID equals {@code userId}, the check
     * passes (same user).
     * 4) If a user exists and the found user's ID differs from {@code userId},
     * throw a {@link ConflictException}.
     *
     * @param email  the email address to validate (case-insensitive)
     * @param userId the ID of the user currently being updated
     * @throws ConflictException if another user already has the given email
     */
    public void ensureNoOtherUserHasEmail(String email, UUID userId) {
        Optional<User> optionalUser = userRepository.findByEmailIgnoreCase(email);
        if (optionalUser.isPresent() && !optionalUser.get().getId().equals(userId)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.USER_EMAIL_TAKEN_BY_OTHER, email, userId);
            throw new ConflictException(
                    ExceptionDomainIdentityError.STA_USER_EMAIL_ALREADY_EXISTS,
                    Map.of(UserConstant.FIELD_USER_EMAIL, email));
        }
    }

    /**
     * Ensures that the given Keycloak ID does not belong to a soft-deleted user.
     *
     * Deleting a user leaves the Keycloak account in place, so the person can
     * still obtain tokens. Without this check the next login would find no live
     * user for the subject and register a fresh account, undoing the deletion.
     *
     * @param keycloakId the Keycloak user ID from the JWT "sub" claim
     * @throws ForbiddenException if a soft-deleted user has this Keycloak ID
     */
    public void ensureKeycloakIdNotDeleted(String keycloakId) {
        if (userRepository.existsDeletedByKeycloakId(keycloakId)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.USER_KEYCLOAK_ID_DELETED, keycloakId);
            throw new ForbiddenException(
                    ExceptionDomainIdentityError.SEC_USER_DELETED,
                    Map.of(UserConstant.FIELD_USER_KEYCLOAK_ID, keycloakId));
        }
    }

    public void ensureUserStatusActive(User user) {
        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.USER_STATUS_NOT_ACTIVE, user.getId());
            throw new ConflictException(
                    ExceptionDomainIdentityError.STA_USER_IS_NOT_ACTIVE,
                    Map.of(UserConstant.FIELD_USER_DATABASE_ID, user.getId()));
        }
    }

    /**
     * Ensures the user is a member of the specified organization.
     *
     * Used before setting a default organization to verify the user actually
     * belongs to that organization.
     *
     * @param userId         the ID of the user
     * @param organizationId the ID of the organization to check membership for
     * @throws ValidationException if the user is not a member of the organization
     */
    public void ensureUserIsMemberOfOrganization(UUID userId, UUID organizationId) {
        if (organizationMemberQueryService.findMember(organizationId, userId).isEmpty()) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    UserExceptionLogConstant.USER_NOT_MEMBER_OF_ORGANIZATION, userId, organizationId);
            throw new ValidationException(
                    ExceptionDomainIdentityError.VAL_USER_NOT_MEMBER_OF_ORGANIZATION,
                    Map.of(
                            UserConstant.FIELD_USER_DATABASE_ID, userId,
                            OrganizationConstant.FIELD_ORGANIZATION_ID, organizationId));
        }
    }
}
