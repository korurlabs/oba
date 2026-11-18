package com.korur.oba.shrine.onboarding.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.onboarding.dto.request.OnboardingCompleteRequest;
import com.korur.oba.shrine.onboarding.dto.response.OnboardingStatusResponse;

/**
 * Service interface for onboarding write operations.
 * <p>
 * Follows CQRS pattern by separating write operations from read operations.
 */
public interface OnboardingService {

    /**
     * Completes the onboarding process for the current user.
     * Creates a personal organization if company information is provided.
     */
    OnboardingStatusResponse completeOnboarding(User user, OnboardingCompleteRequest request);
}
