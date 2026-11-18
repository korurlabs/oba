package com.korur.oba.shrine.onboarding.mapper;

// 1. Framework-level
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.identity.dto.request.UserSelfUpdateRequest;
import com.korur.oba.shrine.identity.dto.request.embedded.UserProfileEmbeddedUpdateRequest;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.onboarding.dto.request.OnboardingCompleteRequest;
import com.korur.oba.shrine.onboarding.dto.response.OnboardingStatusResponse;
import com.korur.oba.shrine.organization.dto.request.OrganizationCreateRequest;
import com.korur.oba.shrine.organization.entity.Organization;

/**
 * MapStruct mapper for the onboarding domain.
 *
 * Covers:
 * - User entity to OnboardingStatusResponse
 * - OnboardingCompleteRequest to UserSelfUpdateRequest (auto-chains to UserProfileEmbeddedUpdateRequest)
 * - OnboardingCompleteRequest to UserProfileEmbeddedUpdateRequest
 * - OnboardingCompleteRequest to OrganizationCreateRequest
 */
@Mapper(componentModel = "spring")
public interface OnboardingMapper {

    // -------------------------------------------------------------------------
    // Response mapping
    // -------------------------------------------------------------------------

    /**
     * Maps a User entity to an OnboardingStatusResponse DTO.
     *
     * @param user the user entity
     * @return the onboarding status response DTO
     */
    @Mapping(source = "id", target = "userId")
    @Mapping(source = "status", target = "hasCompletedOnboarding", qualifiedByName = "isOnboardingCompleted")
    @Mapping(source = "defaultOrganization", target = "hasDefaultOrganization", qualifiedByName = "isDefaultOrganizationSet")
    @Mapping(source = "defaultOrganization.id", target = "defaultOrganizationId")
    OnboardingStatusResponse toStatusResponse(User user);

    /**
     * Converts a UserStatus to a boolean indicating onboarding completion.
     *
     * @param status the user status
     * @return true if the user status is ACTIVE
     */
    @Named("isOnboardingCompleted")
    default boolean isOnboardingCompleted(UserStatus status) {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Converts an Organization reference to a boolean indicating presence.
     *
     * @param organization the organization entity (nullable)
     * @return true if the organization is not null
     */
    @Named("isDefaultOrganizationSet")
    default boolean isDefaultOrganizationSet(Organization organization) {
        return organization != null;
    }

    // -------------------------------------------------------------------------
    // Request transformations
    // -------------------------------------------------------------------------

    /**
     * Maps onboarding completion fields to a UserSelfUpdateRequest.
     * MapStruct auto-chains toProfileUpdateRequest for the profile field.
     * All other UserSelfUpdateRequest fields are ignored (set to null).
     *
     * @param request the onboarding complete request
     * @return the user update request
     */
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "defaultOrganizationId", ignore = true)
    @Mapping(target = "userPreferences", ignore = true)
    @Mapping(source = "request", target = "profile", qualifiedByName = "toProfileFromOnboarding")
    UserSelfUpdateRequest toUserUpdateRequest(OnboardingCompleteRequest request);

    /**
     * Maps onboarding completion fields to a UserProfileEmbeddedUpdateRequest.
     * Only firstName and lastName are carried over; all other profile fields are null.
     *
     * @param request the onboarding complete request
     * @return the profile update request
     */
    @Named("toProfileFromOnboarding")
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "userPosition", ignore = true)
    @Mapping(target = "userPreferredLanguage", ignore = true)
    @Mapping(target = "userGender", ignore = true)
    UserProfileEmbeddedUpdateRequest toProfileUpdateRequest(OnboardingCompleteRequest request);

    /**
     * Maps onboarding completion fields to an OrganizationCreateRequest.
     * Fields with matching names auto-map; description passes through.
     *
     * @param request the onboarding complete request
     * @return the organization create request
     */
    OrganizationCreateRequest toOrganizationCreateRequest(OnboardingCompleteRequest request);
}
