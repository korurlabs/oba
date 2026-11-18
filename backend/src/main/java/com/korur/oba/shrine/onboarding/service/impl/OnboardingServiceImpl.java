package com.korur.oba.shrine.onboarding.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserProfileService;
import com.korur.oba.shrine.identity.service.UserService;
import com.korur.oba.shrine.onboarding.constant.service.OnboardingServiceLogConstant;
import com.korur.oba.shrine.onboarding.dto.request.OnboardingCompleteRequest;
import com.korur.oba.shrine.onboarding.dto.response.OnboardingStatusResponse;
import com.korur.oba.shrine.onboarding.mapper.OnboardingMapper;
import com.korur.oba.shrine.onboarding.service.OnboardingService;
import com.korur.oba.shrine.organization.dto.response.OrganizationDetailResponse;
import com.korur.oba.shrine.organization.service.OrganizationService;

/**
 * Implements write operations for the onboarding flow.
 *
 * Orchestrates calls to identity and organization domain services.
 * Does not access other domains' repositories directly.
 */
@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OnboardingServiceImpl.class);

    private final OnboardingMapper onboardingMapper;
    private final UserProfileService userProfileService;
    private final UserService userService;
    private final OrganizationService organizationService;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public OnboardingStatusResponse completeOnboarding(User user, OnboardingCompleteRequest request) {
        // Update user profile via identity domain service.
        userProfileService.updateProfile(user.getId(), onboardingMapper.toUserUpdateRequest(request));

        // Create organization if company information is provided.
        if (request.name() != null && !request.name().isBlank()) {
            OrganizationDetailResponse orgResponse = organizationService.createOrganization(
                    user, onboardingMapper.toOrganizationCreateRequest(request));

            // Set as default organization via identity domain service.
            userService.setDefaultOrganization(user.getId(), orgResponse.id());

            loggingService.info(
                    LOG_PREFIX,
                    LogLayer.SERVICE,
                    OnboardingServiceLogConstant.ONBOARDING_ORG_CREATED,
                    user.getId());
        }

        // Activate user via identity domain service.
        User updatedUser = userService.activateUser(user.getId());

        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                OnboardingServiceLogConstant.ONBOARDING_COMPLETED,
                updatedUser.getId());

        return onboardingMapper.toStatusResponse(updatedUser);
    }
}
