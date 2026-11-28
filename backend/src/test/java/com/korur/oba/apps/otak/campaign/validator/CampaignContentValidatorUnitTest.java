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
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class CampaignContentValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();
    private final Instant moment = Instant.parse("2026-12-01T08:30:00Z");
    private final Instant now = Instant.parse("2026-12-02T09:00:00Z");

    @Mock
    private CampaignContentRepository campaignContentRepository;

    @Mock
    private LoggingService loggingService;

    private CampaignContentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CampaignContentValidator(campaignContentRepository, Clock.fixed(now, ZoneOffset.UTC),
                loggingService);
    }

    private static String codeOf(Throwable exception) {
        return ((ApplicationException) exception).getErrorCode();
    }

    @Nested
    class EnsureContentExists {

        @Test
        void contentOfThisCampaign_isReturned() {
            // Arrange
            UUID contentId = UUID.randomUUID();
            CampaignContent content = CampaignContent.builder().campaignId(campaignId).build();
            when(campaignContentRepository.findByIdAndCampaignIdAndOrganizationId(contentId, campaignId,
                    organizationId)).thenReturn(Optional.of(content));

            // Act
            CampaignContent result = validator.ensureContentExists(organizationId, campaignId, contentId);

            // Assert
            assertThat(result).isSameAs(content);
        }

        @Test
        void contentOfAnotherCampaignOrOrganization_throwsNotFound() {
            // Arrange
            UUID contentId = UUID.randomUUID();
            when(campaignContentRepository.findByIdAndCampaignIdAndOrganizationId(contentId, campaignId,
                    organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> validator.ensureContentExists(organizationId, campaignId, contentId))
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_CONTENT_FOUND.getCode()));
        }

        @Test
        void unknownIdInAnyOrganization_throwsNotFound() {
            // Arrange
            UUID contentId = UUID.randomUUID();
            when(campaignContentRepository.findById(contentId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> validator.ensureContentExists(contentId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class EnsureStatusGiven {

        @Test
        void missingStatus_throwsFieldValidationOnStatus() {
            assertThatThrownBy(() -> validator.ensureStatusGiven(UUID.randomUUID(), null))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionValidationError.VALIDATION.getCode()))
                    .satisfies(exception -> assertThat(((ValidationException) exception).getErrorValues())
                            .containsEntry(CampaignConstant.FIELD_STATUS, CampaignConstant.STATUS_REQUIRED_MESSAGE));
        }

        @Test
        void givenStatus_passes() {
            assertThatCode(() -> validator.ensureStatusGiven(UUID.randomUUID(), CampaignStatus.DRAFT))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureStatusDates {

        @Test
        void scheduledWithoutPlannedAt_throwsValidation() {
            assertThatThrownBy(() -> validator.ensureStatusDates(CampaignStatus.SCHEDULED, null, null))
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionDomainOtakError.VAL_CAMPAIGN_CONTENT_STATUS_DATES.getCode()));
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

        @ParameterizedTest
        @EnumSource(value = CampaignStatus.class, names = {"PUBLISHED", "ARCHIVED"})
        void publishedAtInTheFuture_throwsValidation(CampaignStatus status) {
            assertThatThrownBy(() -> validator.ensureStatusDates(status, null, now.plusSeconds(1)))
                    .satisfies(exception -> assertThat(codeOf(exception))
                            .isEqualTo(ExceptionDomainOtakError.VAL_CAMPAIGN_CONTENT_STATUS_DATES.getCode()));
        }

        @Test
        void publishedAtExactlyNow_passes() {
            assertThatCode(() -> validator.ensureStatusDates(CampaignStatus.PUBLISHED, null, now))
                    .doesNotThrowAnyException();
        }

        @Test
        void draftWithOnlyPlannedAt_passes() {
            assertThatCode(() -> validator.ensureStatusDates(CampaignStatus.DRAFT, moment, null))
                    .doesNotThrowAnyException();
        }
    }
}
