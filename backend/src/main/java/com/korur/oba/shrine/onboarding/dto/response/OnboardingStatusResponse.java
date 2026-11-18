package com.korur.oba.shrine.onboarding.dto.response;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.enums.UserStatus;

/**
 * Response indicating user's onboarding status.
 */
public record OnboardingStatusResponse(
        UUID userId,
        String email,
        UserStatus status,
        boolean hasCompletedOnboarding,
        boolean hasDefaultOrganization,
        UUID defaultOrganizationId
) {
}
