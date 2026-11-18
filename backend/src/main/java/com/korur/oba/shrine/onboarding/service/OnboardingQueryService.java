package com.korur.oba.shrine.onboarding.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.onboarding.dto.response.OnboardingStatusResponse;

/**
 * Service interface for onboarding read operations.
 * <p>
 * Follows CQRS pattern by separating read operations from write operations.
 */
public interface OnboardingQueryService {

    /**
     * Gets the current user's onboarding status.
     */
    OnboardingStatusResponse getOnboardingStatus(User user);
}
