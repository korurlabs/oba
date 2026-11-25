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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.helper.CampaignOwnerHelper;
import com.korur.oba.apps.otak.campaign.mapper.CampaignMapperImpl;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.campaign.repository.CampaignRepository;
import com.korur.oba.apps.otak.campaign.validator.CampaignValidator;
import com.korur.oba.apps.otak.event.service.EventQueryService;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.icp.service.IcpQueryService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignContentRepository campaignContentRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private IcpQueryService icpQueryService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private LoggingService loggingService;

    private CampaignServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator, mapper and owner helper run over the mocked repositories, so the
        // error codes and the mapped fields are the production ones.
        CampaignValidator validator = new CampaignValidator(campaignRepository, organizationMemberQueryService,
                appAccessValidator, loggingService);
        service = new CampaignServiceImpl(campaignRepository, campaignContentRepository, new CampaignMapperImpl(),
                validator, new CampaignOwnerHelper(userQueryService), eventQueryService, icpQueryService,
                attachmentService, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private static CampaignCreateRequest createRequest(String name, LocalDate startDate, LocalDate endDate,
                                                       UUID eventId) {
        return new CampaignCreateRequest(name, "Night watch", startDate, endDate, null, eventId, null);
    }

    private static CampaignUpdateRequest updateRequest(String name, LocalDate startDate, LocalDate endDate) {
        return new CampaignUpdateRequest(name, null, startDate, endDate, null, null, null);
    }

    private void saveReturnsArgument() {
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign campaign = invocation.getArgument(0);
            campaign.setId(UUID.randomUUID());
            return campaign;
        });
    }

    private Campaign stored() {
        Campaign campaign = Campaign.builder().name("Old").organizationId(organizationId).build();
        campaign.setId(UUID.randomUUID());
        when(campaignRepository.findByIdAndOrganizationId(campaign.getId(), organizationId))
                .thenReturn(Optional.of(campaign));
        return campaign;
    }

    @Nested
    class Create {

        @Test
        void validRequest_storesTrimmedNameInTheHeaderOrganizationWithNoContents() {
            // Arrange
            saveReturnsArgument();

            // Act
            CampaignDetailResponse result = service.create(organizationId, user,
                    createRequest("  De wacht  ", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), null));

            // Assert
            ArgumentCaptor<Campaign> saved = ArgumentCaptor.forClass(Campaign.class);
            verify(campaignRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("De wacht");
            assertThat(saved.getValue().getDescription()).isEqualTo("Night watch");
            assertThat(saved.getValue().getOrganizationId()).isEqualTo(organizationId);
            assertThat(result.contentCount()).isZero();
            assertThat(result.owner()).isNull();
        }

        @Test
        void endBeforeStart_throwsValidationAndSavesNothing() {
            assertThatThrownBy(() -> service.create(organizationId, user,
                    createRequest("De wacht", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 1), null)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CAMPAIGN_DATE_RANGE.getCode()));
            verify(campaignRepository, never()).save(any(Campaign.class));
        }

        @Test
        void trimmedNameTaken_throwsConflictAndSavesNothing() {
            // Arrange
            when(campaignRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "De wacht"))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequest(" De wacht ", null, null,
                    null)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_CAMPAIGN_NAME_TAKEN.getCode()));
            verify(campaignRepository, never()).save(any(Campaign.class));
        }

        @Test
        void eventOfAnotherOrganization_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            doThrow(new NotFoundException(ExceptionDomainOtakError.NOT_EVENT_FOUND))
                    .when(eventQueryService).ensureExists(organizationId, eventId);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequest("De wacht", null, null,
                    eventId)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_EVENT_FOUND.getCode()));
            verify(campaignRepository, never()).save(any(Campaign.class));
        }
    }

    @Nested
    class Update {

        @Test
        void validRequest_replacesTheFieldsAndAnswersWithTheContentCount() {
            // Arrange
            Campaign campaign = stored();
            when(campaignRepository.saveAndFlush(campaign)).thenReturn(campaign);
            when(campaignContentRepository.countByCampaignId(campaign.getId())).thenReturn(4L);

            // Act
            CampaignDetailResponse result = service.update(organizationId, campaign.getId(),
                    updateRequest(" New ", LocalDate.of(2026, 12, 1), null));

            // Assert
            assertThat(campaign.getName()).isEqualTo("New");
            assertThat(campaign.getStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(campaign.getOrganizationId()).isEqualTo(organizationId);
            assertThat(result.contentCount()).isEqualTo(4L);
            verify(campaignRepository).existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "New",
                    campaign.getId());
        }

        @Test
        void unchangedOwner_isNotCheckedAgain() {
            // Arrange
            Campaign campaign = stored();
            when(campaignRepository.saveAndFlush(campaign)).thenReturn(campaign);

            // Act
            service.update(organizationId, campaign.getId(), updateRequest("Old", null, null));

            // Assert
            verify(organizationMemberQueryService, never()).findMember(any(), any());
        }

        @Test
        void endBeforeStart_throwsValidationAndSavesNothing() {
            // Arrange
            Campaign campaign = stored();

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, campaign.getId(),
                    updateRequest("Old", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 1))))
                    .isInstanceOf(ValidationException.class);
            verify(campaignRepository, never()).saveAndFlush(any(Campaign.class));
        }

        @Test
        void campaignMissing_throwsNotFound() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, campaignId, updateRequest("New", null, null)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND.getCode()));
        }
    }

    @Nested
    class Delete {

        @Test
        void ownCampaign_deletesItThenTheImagesOfEveryContent() {
            // Arrange
            Campaign campaign = stored();
            UUID firstContentId = UUID.randomUUID();
            UUID secondContentId = UUID.randomUUID();
            when(campaignContentRepository.findIdsByCampaignId(campaign.getId()))
                    .thenReturn(List.of(firstContentId, secondContentId));

            // Act
            service.delete(organizationId, campaign.getId());

            // Assert
            InOrder order = inOrder(campaignRepository, attachmentService);
            order.verify(campaignRepository).delete(campaign);
            order.verify(campaignRepository).flush();
            order.verify(attachmentService)
                    .deleteAttachmentsByEntity(StorageEntityType.CAMPAIGN_CONTENT, firstContentId);
            order.verify(attachmentService)
                    .deleteAttachmentsByEntity(StorageEntityType.CAMPAIGN_CONTENT, secondContentId);
        }

        @Test
        void campaignMissing_throwsNotFoundAndDeletesNothing() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, campaignId)).isInstanceOf(NotFoundException.class);
            verify(campaignRepository, never()).delete(any(Campaign.class));
            verify(attachmentService, never()).deleteAttachmentsByEntity(any(), any());
        }
    }
}
