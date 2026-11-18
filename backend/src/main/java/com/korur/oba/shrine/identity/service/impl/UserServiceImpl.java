package com.korur.oba.shrine.identity.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.constant.service.UserServiceLogConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.service.UserService;
import com.korur.oba.shrine.identity.validator.UserValidator;
import com.korur.oba.shrine.onboarding.exception.ExceptionDomainOnboardingError;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

/**
 * Implements write operations on user accounts that the platform runs on a user's behalf:
 * onboarding activation, the default organization, and the effects of an organization deletion.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final OrganizationQueryService organizationQueryService;
    private final UserValidator userValidator;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public User activateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND));

        if (user.getStatus() != UserStatus.PENDING_ONBOARDING) {
            throw new ConflictException(
                    ExceptionDomainOnboardingError.STA_ALREADY_ONBOARDED,
                    Map.of("userId", userId.toString(), "currentStatus", user.getStatus().toString()));
        }

        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);

        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                UserServiceLogConstant.USER_ACTIVATED,
                savedUser.getId());

        return savedUser;
    }

    @Override
    @Transactional
    public void setDefaultOrganization(UUID userId, UUID organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND));

        userValidator.ensureUserIsMemberOfOrganization(userId, organizationId);

        Organization organization = organizationQueryService.getOrganizationEntityById(organizationId);

        user.setDefaultOrganization(organization);
        userRepository.save(user);

        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                UserServiceLogConstant.USER_DEFAULT_ORG_SET,
                user.getId(),
                organizationId);
    }

    @Override
    @Transactional
    public void applyOrganizationDeletion(UUID userId, UUID organizationId) {
        // Inside the caller's transaction this returns the user already in the persistence context.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ExceptionDomainIdentityError.NOT_USER_FOUND));

        List<OrganizationMember> otherMemberships = user.getOrganizationMemberships().stream()
                .filter(membership -> !membership.getOrganization().getId().equals(organizationId))
                .toList();

        // Only an active membership may become the default; a disabled one would block every request.
        Organization defaultOrganization = user.getDefaultOrganization();
        if (Objects.nonNull(defaultOrganization) && defaultOrganization.getId().equals(organizationId)) {
            Organization nextDefault = otherMemberships.stream()
                    .filter(membership -> membership.getStatus() == OrganizationMemberStatus.ACTIVE)
                    .map(OrganizationMember::getOrganization)
                    .findFirst()
                    .orElse(null);
            user.setDefaultOrganization(nextDefault);
        }

        if (otherMemberships.isEmpty()) {
            user.setStatus(UserStatus.PENDING_ONBOARDING);
        }

        userRepository.save(user);

        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                UserServiceLogConstant.USER_ORG_DELETION_APPLIED,
                user.getId(),
                organizationId);
    }
}
