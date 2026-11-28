package com.korur.oba.apps.otak.campaign.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.campaign.mapper.CampaignContentMapperImpl;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.campaign.repository.CampaignRepository;
import com.korur.oba.apps.otak.campaign.validator.CampaignContentValidator;
import com.korur.oba.apps.otak.campaign.validator.CampaignValidator;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class CampaignContentServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:15:00Z");

    private final UUID organizationId = UUID.randomUUID();
    private final UUID legalEntityId = UUID.randomUUID();

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignContentRepository campaignContentRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private LegalEntityQueryService legalEntityQueryService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private LoggingService loggingService;

    private CampaignContentServiceImpl service;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        // The real validators and mapper run over the mocked repositories, so the error codes
        // and the mapped fields are the production ones.
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CampaignValidator campaignValidator = new CampaignValidator(campaignRepository, organizationMemberQueryService,
                appAccessValidator, loggingService);
        CampaignContentValidator contentValidator = new CampaignContentValidator(campaignContentRepository, clock,
                loggingService);
        service = new CampaignContentServiceImpl(campaignContentRepository, new CampaignContentMapperImpl(),
                contentValidator, campaignValidator, legalEntityQueryService, attachmentService, clock,
                loggingService);
        campaign = Campaign.builder().name("De wacht").organizationId(organizationId).build();
        campaign.setId(UUID.randomUUID());
    }

    private void campaignStored() {
        when(campaignRepository.findByIdAndOrganizationId(campaign.getId(), organizationId))
                .thenReturn(Optional.of(campaign));
    }

    private void legalEntityOfOrganization() {
        LegalEntity legalEntity = LegalEntity.builder().shortName("Korur B.V.").build();
        legalEntity.setId(legalEntityId);
        when(legalEntityQueryService.getEntity(organizationId, legalEntityId)).thenReturn(legalEntity);
    }

    private void saveReturnsArgument() {
        when(campaignContentRepository.saveAndFlush(any(CampaignContent.class))).thenAnswer(invocation -> {
            CampaignContent content = invocation.getArgument(0);
            if (content.getId() == null) {
                content.setId(UUID.randomUUID());
            }
            return content;
        });
    }

    private CampaignContentRequest request(CampaignStatus status, Instant plannedAt, Instant publishedAt) {
        return new CampaignContentRequest(legalEntityId, CampaignChannel.LINKEDIN, DocumentLanguage.NL,
                "Terwijl u slaapt, houden wij de wacht.", status, plannedAt, publishedAt, null);
    }

    private CampaignContent storedContent() {
        CampaignContent content = CampaignContent.builder().campaignId(campaign.getId()).organizationId(organizationId)
                .legalEntityId(legalEntityId).channel(CampaignChannel.X).language(DocumentLanguage.EN).text("Old")
                .status(CampaignStatus.DRAFT).build();
        content.setId(UUID.randomUUID());
        when(campaignContentRepository.findByIdAndCampaignIdAndOrganizationId(content.getId(), campaign.getId(),
                organizationId)).thenReturn(Optional.of(content));
        return content;
    }

    @Nested
    class Create {

        @Test
        void noStatus_storesDraftOnTheCampaignWithTheLegalEntityShortName() {
            // Arrange
            campaignStored();
            legalEntityOfOrganization();
            saveReturnsArgument();

            // Act
            CampaignContentResponse result = service.create(organizationId, campaign.getId(),
                    request(null, null, null));

            // Assert
            ArgumentCaptor<CampaignContent> saved = ArgumentCaptor.forClass(CampaignContent.class);
            verify(campaignContentRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getCampaignId()).isEqualTo(campaign.getId());
            assertThat(saved.getValue().getOrganizationId()).isEqualTo(organizationId);
            assertThat(saved.getValue().getStatus()).isEqualTo(CampaignStatus.DRAFT);
            assertThat(saved.getValue().getPublishedAt()).isNull();
            assertThat(result.legalEntityShortName()).isEqualTo("Korur B.V.");
            assertThat(result.campaignId()).isEqualTo(campaign.getId());
        }

        @Test
        void publishedWithoutPublishedAt_storesTheClocksNow() {
            // Arrange
            campaignStored();
            legalEntityOfOrganization();
            saveReturnsArgument();

            // Act
            CampaignContentResponse result = service.create(organizationId, campaign.getId(),
                    request(CampaignStatus.PUBLISHED, null, null));

            // Assert
            assertThat(result.publishedAt()).isEqualTo(NOW);
        }

        @Test
        void publishedWithPastPublishedAt_keepsIt() {
            // Arrange
            campaignStored();
            legalEntityOfOrganization();
            saveReturnsArgument();
            Instant past = Instant.parse("2026-10-01T07:00:00Z");

            // Act
            CampaignContentResponse result = service.create(organizationId, campaign.getId(),
                    request(CampaignStatus.PUBLISHED, null, past));

            // Assert
            assertThat(result.publishedAt()).isEqualTo(past);
        }

        @Test
        void scheduledWithoutPlannedAt_throwsValidationAndSavesNothing() {
            // Arrange
            campaignStored();
            legalEntityOfOrganization();

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, campaign.getId(),
                    request(CampaignStatus.SCHEDULED, null, null)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CAMPAIGN_CONTENT_STATUS_DATES.getCode()));
            verify(campaignContentRepository, never()).saveAndFlush(any(CampaignContent.class));
        }

        @Test
        void legalEntityOfAnotherOrganization_throwsNotFoundAndSavesNothing() {
            // Arrange
            campaignStored();
            when(legalEntityQueryService.getEntity(organizationId, legalEntityId))
                    .thenThrow(new NotFoundException(ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND));

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, campaign.getId(), request(null, null, null)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND.getCode()));
            verify(campaignContentRepository, never()).saveAndFlush(any(CampaignContent.class));
        }

        @Test
        void campaignMissing_throwsNotFound() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, campaignId, request(null, null, null)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND.getCode()));
            verify(legalEntityQueryService, never()).getEntity(any(), any());
        }
    }

    @Nested
    class Update {

        @Test
        void validRequest_replacesEveryFieldAndKeepsTheCampaign() {
            // Arrange
            campaignStored();
            CampaignContent content = storedContent();
            legalEntityOfOrganization();
            saveReturnsArgument();
            Instant plannedAt = Instant.parse("2026-12-01T08:30:00Z");

            // Act
            CampaignContentResponse result = service.update(organizationId, campaign.getId(), content.getId(),
                    request(CampaignStatus.SCHEDULED, plannedAt, null));

            // Assert
            assertThat(content.getText()).isEqualTo("Terwijl u slaapt, houden wij de wacht.");
            assertThat(content.getLanguage()).isEqualTo(DocumentLanguage.NL);
            assertThat(content.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
            assertThat(content.getPlannedAt()).isEqualTo(plannedAt);
            assertThat(content.getCampaignId()).isEqualTo(campaign.getId());
            assertThat(result.id()).isEqualTo(content.getId());
        }

        @Test
        void markedPublishedWithoutPublishedAt_storesTheClocksNow() {
            // Arrange
            campaignStored();
            CampaignContent content = storedContent();
            legalEntityOfOrganization();
            saveReturnsArgument();

            // Act
            service.update(organizationId, campaign.getId(), content.getId(),
                    request(CampaignStatus.PUBLISHED, null, null));

            // Assert
            assertThat(content.getPublishedAt()).isEqualTo(NOW);
        }

        @Test
        void missingStatus_throwsFieldValidationAndChangesNothing() {
            // Arrange
            campaignStored();
            CampaignContent content = storedContent();

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, campaign.getId(), content.getId(),
                    request(null, null, null)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionValidationError.VALIDATION.getCode()));
            assertThat(content.getText()).isEqualTo("Old");
            verify(campaignContentRepository, never()).saveAndFlush(any(CampaignContent.class));
        }

        @Test
        void contentOfAnotherCampaign_throwsNotFound() {
            // Arrange
            campaignStored();
            UUID contentId = UUID.randomUUID();
            when(campaignContentRepository.findByIdAndCampaignIdAndOrganizationId(contentId, campaign.getId(),
                    organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, campaign.getId(), contentId,
                    request(CampaignStatus.DRAFT, null, null)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_CONTENT_FOUND.getCode()));
        }
    }

    @Nested
    class Delete {

        @Test
        void ownContent_deletesItThenItsImages() {
            // Arrange
            campaignStored();
            CampaignContent content = storedContent();

            // Act
            service.delete(organizationId, campaign.getId(), content.getId());

            // Assert
            InOrder order = inOrder(campaignContentRepository, attachmentService);
            order.verify(campaignContentRepository).delete(content);
            order.verify(campaignContentRepository).flush();
            order.verify(attachmentService)
                    .deleteAttachmentsByEntity(StorageEntityType.CAMPAIGN_CONTENT, content.getId());
        }

        @Test
        void contentMissing_throwsNotFoundAndDeletesNothing() {
            // Arrange
            campaignStored();
            UUID contentId = UUID.randomUUID();
            when(campaignContentRepository.findByIdAndCampaignIdAndOrganizationId(contentId, campaign.getId(),
                    organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, campaign.getId(), contentId))
                    .isInstanceOf(NotFoundException.class);
            verify(campaignContentRepository, never()).delete(any(CampaignContent.class));
            verify(attachmentService, never()).deleteAttachmentsByEntity(any(), any());
        }
    }
}
