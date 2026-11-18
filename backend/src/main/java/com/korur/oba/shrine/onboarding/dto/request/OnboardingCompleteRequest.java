package com.korur.oba.shrine.onboarding.dto.request;

// 1. Framework-level
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Request to complete user onboarding with profile information.
 */
public record OnboardingCompleteRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 255, message = "First name must be at most 255 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 255, message = "Last name must be at most 255 characters")
        String lastName,

        @Size(max = 100, message = "Job title must be at most 100 characters")
        String jobTitle,

        @Size(max = 255, message = "Organization name must be at most 255 characters")
        String name,

        @Size(max = 512, message = "Description must be at most 512 characters")
        String description,

        @Size(max = 100, message = "Industry must be at most 100 characters")
        String industry,

        @Size(max = 50, message = "Company size must be at most 50 characters")
        String companySize,

        @Size(max = 3, message = "Country code must be 3 characters (ISO 3166-1 alpha-3)")
        String country,

        @Size(max = 512, message = "Website must be at most 512 characters")
        String website
) {
}
