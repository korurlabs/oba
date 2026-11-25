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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
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
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:15:00Z");

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
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CampaignValidator validator = new CampaignValidator(campaignRepository, organizationMemberQueryService,
                appAccessValidator, clock, loggingService);
        service = new CampaignServiceImpl(campaignRepository, campaignContentRepository, new CampaignMapperImpl(),
                validator, new CampaignOwnerHelper(userQueryService), eventQueryService, icpQueryService,
                attachmentService, clock, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private static CampaignCreateRequest createRequest(String name, CampaignStatus status, Instant plannedAt,
                                                       Instant publishedAt, UUID eventId,
                                                       List<CampaignContentRequest> contents) {
        return new CampaignCreateRequest(name, CampaignChannel.LINKEDIN, status, plannedAt, publishedAt, null, null,
                eventId, null, contents);
    }

    private static CampaignUpdateRequest updateRequest(CampaignStatus status, Instant publishedAt,
                                                       List<CampaignContentRequest> contents) {
        return new CampaignUpdateRequest(" New ", CampaignChannel.X, status, null, publishedAt, null, null, null, null,
                contents);
    }

    private void saveReturnsArgument() {
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign campaign = invocation.getArgument(0);
            campaign.setId(UUID.randomUUID());
            return campaign;
        });
        when(campaignContentRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Campaign stored() {
        Campaign campaign = Campaign.builder().name("Old").channel(CampaignChannel.LINKEDIN).status(CampaignStatus.DRAFT)
                .organizationId(organizationId).build();
        campaign.setId(UUID.randomUUID());
        when(campaignRepository.findByIdAndOrganizationId(campaign.getId(), organizationId))
                .thenReturn(Optional.of(campaign));
        return campaign;
    }

    private static CampaignContent content(UUID campaignId, DocumentLanguage language, String text) {
        CampaignContent content = CampaignContent.builder().campaignId(campaignId).language(language).text(text).build();
        content.setId(UUID.randomUUID());
        return content;
    }

    @Nested
    class Create {

        @Test
        void noStatus_storesDraftWithTrimmedNameInTheHeaderOrganization() {
            // Arrange
            saveReturnsArgument();

            // Act
            CampaignDetailResponse result = service.create(organizationId, user,
                    createRequest("  IGEF 2026  ", null, null, null, null, null));

            // Assert
            ArgumentCaptor<Campaign> saved = ArgumentCaptor.forClass(Campaign.class);
            verify(campaignRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("IGEF 2026");
            assertThat(saved.getValue().getStatus()).isEqualTo(CampaignStatus.DRAFT);
            assertThat(saved.getValue().getOrganizationId()).isEqualTo(organizationId);
            assertThat(saved.getValue().getPublishedAt()).isNull();
            assertThat(result.status()).isEqualTo(CampaignStatus.DRAFT);
            assertThat(result.contents()).isEmpty();
            assertThat(result.owner()).isNull();
        }

        @Test
        void contents_areStoredForTheNewCampaignInLanguageOrder() {
            // Arrange
            saveReturnsArgument();

            // Act
            CampaignDetailResponse result = service.create(organizationId, user, createRequest("IGEF", null, null, null,
                    null, List.of(new CampaignContentRequest(DocumentLanguage.EN, "Hello"),
                            new CampaignContentRequest(DocumentLanguage.NL, "Hallo"))));

            // Assert
            assertThat(result.languages()).containsExactly(DocumentLanguage.NL, DocumentLanguage.EN);
            assertThat(result.contents()).extracting(CampaignContentResponse::text).containsExactly("Hallo", "Hello");
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CampaignContent>> saved = ArgumentCaptor.forClass(List.class);
            verify(campaignContentRepository).saveAllAndFlush(saved.capture());
            assertThat(saved.getValue()).allSatisfy(content -> assertThat(content.getCampaignId()).isNotNull());
        }

        @Test
        void publishedWithoutPublishedAt_storesTheClocksNow() {
            // Arrange
            saveReturnsArgument();

            // Act
            CampaignDetailResponse result = service.create(organizationId, user,
                    createRequest("IGEF", CampaignStatus.PUBLISHED, null, null, null, null));

            // Assert
            assertThat(result.publishedAt()).isEqualTo(NOW);
        }

        @Test
        void publishedWithPastPublishedAt_keepsIt() {
            // Arrange
            saveReturnsArgument();
            Instant past = Instant.parse("2026-10-01T07:00:00Z");

            // Act
            CampaignDetailResponse result = service.create(organizationId, user,
                    createRequest("IGEF", CampaignStatus.PUBLISHED, null, past, null, null));

            // Assert
            assertThat(result.publishedAt()).isEqualTo(past);
        }

        @Test
        void publishedWithFuturePublishedAt_throwsValidationAndSavesNothing() {
            assertThatThrownBy(() -> service.create(organizationId, user,
                    createRequest("IGEF", CampaignStatus.PUBLISHED, null, NOW.plusSeconds(60), null, null)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CAMPAIGN_STATUS_DATES.getCode()));
            verify(campaignRepository, never()).save(any(Campaign.class));
        }

        @Test
        void scheduledWithoutPlannedAt_throwsValidationAndSavesNothing() {
            assertThatThrownBy(() -> service.create(organizationId, user,
                    createRequest("IGEF", CampaignStatus.SCHEDULED, null, null, null, null)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_CAMPAIGN_STATUS_DATES.getCode()));
            verify(campaignRepository, never()).save(any(Campaign.class));
        }

        @Test
        void trimmedNameTaken_throwsConflictAndSavesNothing() {
            // Arrange
            when(campaignRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "IGEF")).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user,
                    createRequest(" IGEF ", null, null, null, null, null)))
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
            assertThatThrownBy(() -> service.create(organizationId, user,
                    createRequest("IGEF", null, null, null, eventId, null)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_EVENT_FOUND.getCode()));
            verify(campaignRepository, never()).save(any(Campaign.class));
        }
    }

    @Nested
    class Update {

        @Test
        void contents_dropsLeftOutLanguagesOverwritesKeptOnesAndAddsNewOnes() {
            // Arrange
            Campaign campaign = stored();
            CampaignContent nl = content(campaign.getId(), DocumentLanguage.NL, "Hallo");
            CampaignContent tr = content(campaign.getId(), DocumentLanguage.TR, "Merhaba");
            when(campaignRepository.saveAndFlush(campaign)).thenReturn(campaign);
            when(campaignContentRepository.findAllByCampaignIdOrderByLanguageAsc(campaign.getId()))
                    .thenReturn(List.of(nl, tr));
            when(campaignContentRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            CampaignDetailResponse result = service.update(organizationId, campaign.getId(),
                    updateRequest(CampaignStatus.DRAFT, null, List.of(
                            new CampaignContentRequest(DocumentLanguage.EN, "Hello"),
                            new CampaignContentRequest(DocumentLanguage.TR, "Merhaba dunya"))));

            // Assert
            verify(campaignContentRepository).deleteAll(List.of(nl));
            assertThat(tr.getText()).isEqualTo("Merhaba dunya");
            assertThat(result.languages()).containsExactly(DocumentLanguage.TR, DocumentLanguage.EN);
            assertThat(result.contents()).extracting(CampaignContentResponse::text)
                    .containsExactly("Merhaba dunya", "Hello");
            assertThat(campaign.getName()).isEqualTo("New");
            assertThat(campaign.getChannel()).isEqualTo(CampaignChannel.X);
            assertThat(campaign.getOrganizationId()).isEqualTo(organizationId);
            verify(campaignRepository).existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "New",
                    campaign.getId());
        }

        @Test
        void onlyTextsChanged_movesTheCampaignsLastChangeAndAnswersWithIt() {
            // Arrange
            Campaign campaign = stored();
            Campaign touched = Campaign.builder().name("New").channel(CampaignChannel.X).status(CampaignStatus.DRAFT)
                    .organizationId(organizationId).updatedAt(NOW).build();
            touched.setId(campaign.getId());
            when(campaignRepository.findByIdAndOrganizationId(campaign.getId(), organizationId))
                    .thenReturn(Optional.of(campaign), Optional.of(touched));
            when(campaignRepository.saveAndFlush(campaign)).thenReturn(campaign);
            when(campaignContentRepository.findAllByCampaignIdOrderByLanguageAsc(campaign.getId()))
                    .thenReturn(List.of(content(campaign.getId(), DocumentLanguage.NL, "Hallo")));
            when(campaignContentRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            CampaignDetailResponse result = service.update(organizationId, campaign.getId(),
                    updateRequest(CampaignStatus.DRAFT, null,
                            List.of(new CampaignContentRequest(DocumentLanguage.NL, "Hallo wereld"))));

            // Assert
            InOrder order = inOrder(campaignContentRepository, campaignRepository);
            order.verify(campaignContentRepository).saveAllAndFlush(anyList());
            order.verify(campaignRepository).touchUpdatedAt(campaign.getId(), NOW);
            assertThat(result.updatedAt()).isEqualTo(NOW);
        }

        @Test
        void textsUnchanged_leavesTheLastChangeToAuditing() {
            // Arrange
            Campaign campaign = stored();
            when(campaignRepository.saveAndFlush(campaign)).thenReturn(campaign);
            when(campaignContentRepository.findAllByCampaignIdOrderByLanguageAsc(campaign.getId()))
                    .thenReturn(List.of(content(campaign.getId(), DocumentLanguage.NL, "Hallo")));
            when(campaignContentRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            service.update(organizationId, campaign.getId(), updateRequest(CampaignStatus.DRAFT, null,
                    List.of(new CampaignContentRequest(DocumentLanguage.NL, "Hallo"))));

            // Assert
            verify(campaignRepository, never()).touchUpdatedAt(any(), any());
        }

        @Test
        void campaignRowAndTextsChanged_leavesTheLastChangeToAuditing() {
            // Arrange
            Campaign campaign = stored();
            Campaign saved = Campaign.builder().name("New").channel(CampaignChannel.X).status(CampaignStatus.DRAFT)
                    .organizationId(organizationId).updatedAt(NOW).build();
            saved.setId(campaign.getId());
            when(campaignRepository.saveAndFlush(campaign)).thenReturn(saved);
            when(campaignContentRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            CampaignDetailResponse result = service.update(organizationId, campaign.getId(),
                    updateRequest(CampaignStatus.DRAFT, null,
                            List.of(new CampaignContentRequest(DocumentLanguage.EN, "Hello"))));

            // Assert
            verify(campaignRepository, never()).touchUpdatedAt(any(), any());
            assertThat(result.updatedAt()).isEqualTo(NOW);
        }

        @Test
        void markedPublishedWithoutPublishedAt_storesTheClocksNow() {
            // Arrange
            Campaign campaign = stored();
            when(campaignRepository.saveAndFlush(campaign)).thenReturn(campaign);
            when(campaignContentRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            CampaignDetailResponse result = service.update(organizationId, campaign.getId(),
                    updateRequest(CampaignStatus.PUBLISHED, null, null));

            // Assert
            assertThat(result.status()).isEqualTo(CampaignStatus.PUBLISHED);
            assertThat(result.publishedAt()).isEqualTo(NOW);
        }

        @Test
        void repeatedLanguage_throwsValidationAndChangesNothing() {
            // Arrange
            Campaign campaign = stored();

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, campaign.getId(),
                    updateRequest(CampaignStatus.DRAFT, null, List.of(
                            new CampaignContentRequest(DocumentLanguage.NL, "Hallo"),
                            new CampaignContentRequest(DocumentLanguage.NL, "Hoi")))))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ExceptionDomainOtakError.VAL_CAMPAIGN_CONTENT_LANGUAGE_REPEATED.getCode()));
            verify(campaignRepository, never()).saveAndFlush(any(Campaign.class));
            verify(campaignContentRepository, never()).saveAllAndFlush(anyList());
        }

        @Test
        void campaignMissing_throwsNotFound() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, campaignId,
                    updateRequest(CampaignStatus.DRAFT, null, null)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND.getCode()));
        }
    }

    @Nested
    class Delete {

        @Test
        void ownCampaign_deletesItThenItsAttachments() {
            // Arrange
            Campaign campaign = stored();

            // Act
            service.delete(organizationId, campaign.getId());

            // Assert
            InOrder order = inOrder(campaignRepository, attachmentService);
            order.verify(campaignRepository).delete(campaign);
            order.verify(campaignRepository).flush();
            order.verify(attachmentService).deleteAttachmentsByEntity(StorageEntityType.CAMPAIGN, campaign.getId());
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
