package com.korur.oba.apps.otak.campaign.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.campaign.repository.CampaignRepository;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class CampaignValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final Instant moment = Instant.parse("2026-12-01T08:30:00Z");

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private LoggingService loggingService;

    private CampaignValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CampaignValidator(campaignRepository, organizationMemberQueryService, appAccessValidator,
                loggingService);
    }

    private static String codeOf(Throwable exception) {
        return ((ApplicationException) exception).getErrorCode();
    }

    @Nested
    class EnsureCampaignExists {

        @Test
        void campaignOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> validator.ensureCampaignExists(organizationId, campaignId))
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND.getCode()));
        }

        @Test
        void unknownIdInAnyOrganization_throwsNotFound() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> validator.ensureCampaignExists(campaignId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class EnsureNameFree {

        @Test
        void nameTakenOnCreate_throwsConflict() {
            // Arrange
            when(campaignRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "IGEF")).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> validator.ensureNameFree(organizationId, "IGEF", null))
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionDomainOtakError.STA_CAMPAIGN_NAME_TAKEN.getCode()));
        }

        @Test
        void ownNameOnUpdate_passes() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(campaignRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "IGEF", campaignId))
                    .thenReturn(false);

            // Act and assert
            assertThatCode(() -> validator.ensureNameFree(organizationId, "IGEF", campaignId)).doesNotThrowAnyException();
            verify(campaignRepository, never()).existsByOrganizationIdAndNameIgnoreCase(any(), any());
        }
    }

    @Nested
    class EnsureStatusDates {

        @Test
        void scheduledWithoutPlannedAt_throwsValidation() {
            assertThatThrownBy(() -> validator.ensureStatusDates(CampaignStatus.SCHEDULED, null, null))
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionDomainOtakError.VAL_CAMPAIGN_STATUS_DATES.getCode()));
        }

        @ParameterizedTest
        @EnumSource(value = CampaignStatus.class, names = {"DRAFT", "SCHEDULED"})
        void unpublishedWithPublishedAt_throwsValidation(CampaignStatus status) {
            assertThatThrownBy(() -> validator.ensureStatusDates(status, moment, moment))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void scheduledWithPlannedAt_passes() {
            assertThatCode(() -> validator.ensureStatusDates(CampaignStatus.SCHEDULED, moment, null))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = CampaignStatus.class, names = {"PUBLISHED", "ARCHIVED"})
        void publishedOrArchived_takesAnyMoments(CampaignStatus status) {
            assertThatCode(() -> validator.ensureStatusDates(status, moment, moment)).doesNotThrowAnyException();
            assertThatCode(() -> validator.ensureStatusDates(status, null, null)).doesNotThrowAnyException();
        }

        @Test
        void draftWithOnlyPlannedAt_passes() {
            assertThatCode(() -> validator.ensureStatusDates(CampaignStatus.DRAFT, moment, null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureLanguagesDistinct {

        @Test
        void repeatedLanguage_throwsValidation() {
            List<CampaignContentRequest> contents = List.of(
                    new CampaignContentRequest(DocumentLanguage.NL, "Hallo"),
                    new CampaignContentRequest(DocumentLanguage.NL, "Goedendag"));

            assertThatThrownBy(() -> validator.ensureLanguagesDistinct(contents))
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionDomainOtakError.VAL_CAMPAIGN_CONTENT_LANGUAGE_REPEATED.getCode()));
        }

        @Test
        void distinctLanguagesOrNone_pass() {
            assertThatCode(() -> validator.ensureLanguagesDistinct(List.of(
                    new CampaignContentRequest(DocumentLanguage.NL, "Hallo"),
                    new CampaignContentRequest(DocumentLanguage.EN, "Hello")))).doesNotThrowAnyException();
            assertThatCode(() -> validator.ensureLanguagesDistinct(null)).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureOwnerIsOrganizationMember {

        @Test
        void noOwner_passesWithoutLookup() {
            validator.ensureOwnerIsOrganizationMember(organizationId, null);

            verify(organizationMemberQueryService, never()).findMember(any(), any());
        }

        @Test
        void activeMember_passesAndChecksOtakAccess() {
            // Arrange
            UUID ownerUserId = UUID.randomUUID();
            when(organizationMemberQueryService.findMember(organizationId, ownerUserId))
                    .thenReturn(Optional.of(OrganizationMember.builder().status(OrganizationMemberStatus.ACTIVE).build()));

            // Act
            validator.ensureOwnerIsOrganizationMember(organizationId, ownerUserId);

            // Assert
            verify(appAccessValidator).ensureUsersHaveAppAccess(organizationId, App.OTAK, List.of(ownerUserId));
        }

        @Test
        void inactiveMember_throwsConflict() {
            // Arrange
            UUID ownerUserId = UUID.randomUUID();
            when(organizationMemberQueryService.findMember(organizationId, ownerUserId))
                    .thenReturn(Optional.of(OrganizationMember.builder().status(OrganizationMemberStatus.DISABLED).build()));

            // Act and assert
            assertThatThrownBy(() -> validator.ensureOwnerIsOrganizationMember(organizationId, ownerUserId))
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionDomainOtakError.STA_OWNER_NOT_ORGANIZATION_MEMBER.getCode()));
            verify(appAccessValidator, never()).ensureUsersHaveAppAccess(any(), any(), anyCollection());
        }

        @Test
        void unknownUser_throwsConflict() {
            // Arrange
            UUID ownerUserId = UUID.randomUUID();
            when(organizationMemberQueryService.findMember(organizationId, ownerUserId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> validator.ensureOwnerIsOrganizationMember(organizationId, ownerUserId))
                    .isInstanceOf(ConflictException.class);
        }
    }
}
