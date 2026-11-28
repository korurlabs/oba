package com.korur.oba.apps.otak.relation.service.impl;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.service.CampaignQueryService;
import com.korur.oba.apps.otak.event.service.EventQueryService;
import com.korur.oba.apps.otak.icp.service.IcpQueryService;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.relation.dto.request.RelationCreateRequest;
import com.korur.oba.apps.otak.relation.dto.request.RelationUpdateRequest;
import com.korur.oba.apps.otak.relation.dto.response.RelationDetailResponse;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationSource;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.helper.RelationOwnerHelper;
import com.korur.oba.apps.otak.relation.mapper.RelationMapper;
import com.korur.oba.apps.otak.relation.mapper.RelationMapperImpl;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;
import com.korur.oba.apps.otak.relation.validator.RelationValidator;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;
import com.korur.oba.shrine.document.service.document.DocumentService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentService;

@ExtendWith(MockitoExtension.class)
class RelationServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private RelationRepository relationRepository;

    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;

    @Mock
    private RelationOwnerHelper relationOwnerHelper;

    @Mock
    private IcpQueryService icpQueryService;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private CampaignQueryService campaignQueryService;

    @Mock
    private MailQueryService mailQueryService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private DocumentService documentService;

    @Mock
    private AppAccessValidator appAccessValidator;

    @Mock
    private LoggingService loggingService;

    private RelationServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator and mapper run over the mocked repository, so the error codes and
        // the mapped fields are the production ones.
        RelationValidator validator = new RelationValidator(relationRepository, organizationMemberQueryService,
                appAccessValidator, loggingService);
        RelationMapper mapper = new RelationMapperImpl();
        service = new RelationServiceImpl(relationRepository, mapper, validator, relationOwnerHelper,
                icpQueryService, eventQueryService, campaignQueryService, mailQueryService, attachmentService, documentService, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private static RelationCreateRequest createRequest(RelationType type, UUID ownerUserId) {
        return createRequest(type, null, ownerUserId);
    }

    private static RelationCreateRequest createRequest(RelationType type, PipelineStage stage, UUID ownerUserId) {
        return new RelationCreateRequest("Fabrikam", type, stage, "https://www.fabrikam.example", null, null, null, null, null,
                null, null, Country.NL, null, new BigDecimal("25000.00"), ownerUserId, null, null, null, null);
    }

    private static RelationUpdateRequest updateRequest(UUID ownerUserId) {
        return updateRequest(ownerUserId, null);
    }

    private static RelationUpdateRequest updateRequest(UUID ownerUserId, UUID icpId) {
        return new RelationUpdateRequest("Fabrikam B.V.", null, null, null, null, null, null, null, null, null,
                null, null, ownerUserId, icpId, null, null, "Met at the fair");
    }

    private static RelationCreateRequest createRequestWithIcp(UUID icpId) {
        return new RelationCreateRequest("Fabrikam", RelationType.LEAD, null, null, null, null, null, null, null, null,
                null, Country.NL, null, null, null, icpId, null, null, null);
    }

    private NotFoundException icpNotFound(UUID icpId) {
        return new NotFoundException(ExceptionDomainOtakError.NOT_ICP_FOUND, Map.of("icpId", icpId));
    }

    private Relation storedRelation(RelationType type, PipelineStage stage, UUID ownerUserId) {
        Relation relation = Relation.builder().name("Fabrikam").type(type).stage(stage)
                .organizationId(organizationId).ownerUserId(ownerUserId).build();
        relation.setId(UUID.randomUUID());
        when(relationRepository.findByIdAndOrganizationId(relation.getId(), organizationId))
                .thenReturn(Optional.of(relation));
        return relation;
    }

    private void stubOwner(UUID ownerUserId, OrganizationMemberStatus status) {
        OrganizationMember member = OrganizationMember.builder().status(status).build();
        when(organizationMemberQueryService.findMember(organizationId, ownerUserId)).thenReturn(Optional.of(member));
    }

    private void stubOwnerWithoutOtak(UUID ownerUserId) {
        stubOwner(ownerUserId, OrganizationMemberStatus.ACTIVE);
        doThrow(new ValidationException(ExceptionDomainAccessError.VAL_MEMBER_WITHOUT_APP_ACCESS,
                Map.of("userId", ownerUserId)))
                .when(appAccessValidator).ensureUsersHaveAppAccess(organizationId, App.OTAK, List.of(ownerUserId));
    }

    private Relation captureSaved() {
        ArgumentCaptor<Relation> captor = ArgumentCaptor.forClass(Relation.class);
        verify(relationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    class Create {

        @BeforeEach
        void echoSave() {
            when(relationRepository.save(any(Relation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        void lead_startsAtStageNewInTheHeaderOrganization() {
            // Act
            RelationDetailResponse response = service.create(organizationId, user,
                    createRequest(RelationType.LEAD, null));

            // Assert
            Relation saved = captureSaved();
            assertThat(saved.getStage()).isEqualTo(PipelineStage.NEW);
            assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
            assertThat(response.stage()).isEqualTo(PipelineStage.NEW);
            assertThat(response.type()).isEqualTo(RelationType.LEAD);
            assertThat(response.expectedValue()).isEqualByComparingTo("25000.00");
        }

        @Test
        void customer_startsAtStageDirect() {
            // Act
            RelationDetailResponse response = service.create(organizationId, user,
                    createRequest(RelationType.CUSTOMER, null));

            // Assert
            assertThat(captureSaved().getStage()).isEqualTo(PipelineStage.DIRECT);
            assertThat(response.stage()).isEqualTo(PipelineStage.DIRECT);
        }

        @Test
        void supplier_hasNoStage() {
            service.create(organizationId, user, createRequest(RelationType.SUPPLIER, null));

            assertThat(captureSaved().getStage()).isNull();
        }

        @Test
        void leadWithStageWarm_savesWarm() {
            // Act
            RelationDetailResponse response = service.create(organizationId, user,
                    createRequest(RelationType.LEAD, PipelineStage.WARM, null));

            // Assert
            assertThat(captureSaved().getStage()).isEqualTo(PipelineStage.WARM);
            assertThat(response.stage()).isEqualTo(PipelineStage.WARM);
        }

        @Test
        void activeOwner_isStored() {
            // Arrange
            UUID ownerUserId = UUID.randomUUID();
            stubOwner(ownerUserId, OrganizationMemberStatus.ACTIVE);

            // Act
            service.create(organizationId, user, createRequest(RelationType.LEAD, ownerUserId));

            // Assert
            assertThat(captureSaved().getOwnerUserId()).isEqualTo(ownerUserId);
        }
    }

    @Nested
    class CreateWithRefusedStage {

        @Test
        void leadWithStageClosedOrDirect_throwsStageNotForTypeAndSavesNothing() {
            for (PipelineStage stage : new PipelineStage[] {PipelineStage.CLOSED, PipelineStage.DIRECT}) {
                assertThatThrownBy(() -> service.create(organizationId, user,
                        createRequest(RelationType.LEAD, stage, null)))
                        .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                                assertThat(exception.getErrorCode()).isEqualTo("OTK_STA_006"));
            }
            verify(relationRepository, never()).save(any());
        }

        @Test
        void customerWithStage_throwsStaRelationNotLeadAndSavesNothing() {
            assertThatThrownBy(() -> service.create(organizationId, user,
                    createRequest(RelationType.CUSTOMER, PipelineStage.WARM, null)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_STA_002"));
            verify(relationRepository, never()).save(any());
        }

        @Test
        void supplierWithStage_throwsStaRelationNotLeadAndSavesNothing() {
            assertThatThrownBy(() -> service.create(organizationId, user,
                    createRequest(RelationType.SUPPLIER, PipelineStage.NEW, null)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_STA_002"));
            verify(relationRepository, never()).save(any());
        }
    }

    @Test
    void create_ownerWithoutOtak_throwsMemberWithoutAppAccessAndSavesNothing() {
        // Arrange
        UUID ownerUserId = UUID.randomUUID();
        stubOwnerWithoutOtak(ownerUserId);

        // Act and assert
        assertThatThrownBy(() -> service.create(organizationId, user, createRequest(RelationType.LEAD, ownerUserId)))
                .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                        assertThat(exception.getErrorCode()).isEqualTo("APP_VAL_002"));
        verify(relationRepository, never()).save(any());
    }

    @Test
    void create_ownerNotActiveMember_throwsConflictAndSavesNothing() {
        // Arrange
        UUID ownerUserId = UUID.randomUUID();
        stubOwner(ownerUserId, OrganizationMemberStatus.DISABLED);

        // Act and assert
        assertThatThrownBy(() -> service.create(organizationId, user, createRequest(RelationType.LEAD, ownerUserId)))
                .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.STA_OWNER_NOT_ORGANIZATION_MEMBER.getCode()));
        verify(relationRepository, never()).save(any());
    }

    @Nested
    class IcpLink {

        @Test
        void createWithoutIcp_skipsTheIcpCheck() {
            // Arrange
            when(relationRepository.save(any(Relation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            service.create(organizationId, user, createRequestWithIcp(null));

            // Assert
            verify(icpQueryService, never()).ensureExists(any(), any());
            assertThat(captureSaved().getIcpId()).isNull();
        }

        @Test
        void createWithIcpOfOrganization_checksItAndStoresIt() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            when(relationRepository.save(any(Relation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RelationDetailResponse response = service.create(organizationId, user, createRequestWithIcp(icpId));

            // Assert
            verify(icpQueryService).ensureExists(organizationId, icpId);
            assertThat(captureSaved().getIcpId()).isEqualTo(icpId);
            assertThat(response.icpId()).isEqualTo(icpId);
        }

        @Test
        void createWithForeignIcp_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            doThrow(icpNotFound(icpId)).when(icpQueryService).ensureExists(organizationId, icpId);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequestWithIcp(icpId)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_NOT_004"));
            verify(relationRepository, never()).save(any());
        }

        @Test
        void updateWithoutIcp_skipsTheIcpCheckAndUnlinks() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            relation.setIcpId(UUID.randomUUID());
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            service.update(organizationId, relation.getId(), updateRequest(null, null));

            // Assert
            verify(icpQueryService, never()).ensureExists(any(), any());
            assertThat(relation.getIcpId()).isNull();
        }

        @Test
        void updateWithIcpOfOrganization_checksItAndLinks() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            service.update(organizationId, relation.getId(), updateRequest(null, icpId));

            // Assert
            verify(icpQueryService).ensureExists(organizationId, icpId);
            assertThat(relation.getIcpId()).isEqualTo(icpId);
        }

        @Test
        void updateWithForeignIcp_throwsNotFoundSavesNothingAndKeepsTheLink() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            UUID currentIcpId = UUID.randomUUID();
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            relation.setIcpId(currentIcpId);
            doThrow(icpNotFound(icpId)).when(icpQueryService).ensureExists(organizationId, icpId);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), updateRequest(null, icpId)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_NOT_004"));
            verify(relationRepository, never()).saveAndFlush(any());
            assertThat(relation.getIcpId()).isEqualTo(currentIcpId);
            assertThat(relation.getName()).isEqualTo("Fabrikam");
        }
    }

    @Nested
    class EventLink {

        private RelationCreateRequest createRequestWithEvent(UUID eventId) {
            return new RelationCreateRequest("Fabrikam", RelationType.LEAD, null, null, null, null, null, null, null,
                    null, null, Country.NL, null, null, null, null, eventId, null, null);
        }

        private RelationUpdateRequest updateRequestWithEvent(UUID eventId) {
            return new RelationUpdateRequest("Fabrikam B.V.", null, null, null, null, null, null, null, null, null,
                    null, null, null, null, eventId, null, "Met at the fair");
        }

        private NotFoundException eventNotFound(UUID eventId) {
            return new NotFoundException(ExceptionDomainOtakError.NOT_EVENT_FOUND, Map.of("eventId", eventId));
        }

        @Test
        void createWithoutEvent_skipsTheEventCheck() {
            // Arrange
            when(relationRepository.save(any(Relation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RelationDetailResponse response = service.create(organizationId, user, createRequestWithEvent(null));

            // Assert
            verify(eventQueryService, never()).ensureExists(any(), any());
            assertThat(captureSaved().getEventId()).isNull();
            assertThat(response.eventId()).isNull();
        }

        @Test
        void createWithEventOfOrganization_checksItAndStoresIt() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(relationRepository.save(any(Relation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RelationDetailResponse response = service.create(organizationId, user, createRequestWithEvent(eventId));

            // Assert
            verify(eventQueryService).ensureExists(organizationId, eventId);
            assertThat(captureSaved().getEventId()).isEqualTo(eventId);
            assertThat(response.eventId()).isEqualTo(eventId);
        }

        @Test
        void createWithForeignEvent_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            doThrow(eventNotFound(eventId)).when(eventQueryService).ensureExists(organizationId, eventId);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequestWithEvent(eventId)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_EVENT_FOUND.getCode()));
            verify(relationRepository, never()).save(any());
        }

        @Test
        void updateWithNullEvent_skipsTheCheckAndClearsTheLink() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            relation.setEventId(UUID.randomUUID());
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            service.update(organizationId, relation.getId(), updateRequestWithEvent(null));

            // Assert
            verify(eventQueryService, never()).ensureExists(any(), any());
            assertThat(relation.getEventId()).isNull();
        }

        @Test
        void updateWithEventOfOrganization_checksItAndLinks() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            service.update(organizationId, relation.getId(), updateRequestWithEvent(eventId));

            // Assert
            verify(eventQueryService).ensureExists(organizationId, eventId);
            assertThat(relation.getEventId()).isEqualTo(eventId);
        }

        @Test
        void updateWithForeignEvent_throwsNotFoundSavesNothingAndKeepsTheLink() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID currentEventId = UUID.randomUUID();
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            relation.setEventId(currentEventId);
            doThrow(eventNotFound(eventId)).when(eventQueryService).ensureExists(organizationId, eventId);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(),
                    updateRequestWithEvent(eventId))).isInstanceOf(NotFoundException.class);
            verify(relationRepository, never()).saveAndFlush(any());
            assertThat(relation.getEventId()).isEqualTo(currentEventId);
        }
    }

    @Nested
    class CampaignLink {

        private RelationCreateRequest createRequestWithCampaign(UUID campaignId) {
            return new RelationCreateRequest("Fabrikam", RelationType.LEAD, null, null, null, null, null, null, null,
                    null, null, Country.NL, RelationSource.CAMPAIGN, null, null, null, null, campaignId, null);
        }

        private RelationUpdateRequest updateRequestWithCampaign(UUID campaignId) {
            return new RelationUpdateRequest("Fabrikam B.V.", null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, campaignId, "Met at the fair");
        }

        private NotFoundException campaignNotFound(UUID campaignId) {
            return new NotFoundException(ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND,
                    Map.of("campaignId", campaignId));
        }

        @Test
        void createWithoutCampaign_skipsTheCampaignCheck() {
            // Arrange
            when(relationRepository.save(any(Relation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RelationDetailResponse response = service.create(organizationId, user, createRequestWithCampaign(null));

            // Assert
            verify(campaignQueryService, never()).ensureExists(any(), any());
            assertThat(captureSaved().getCampaignId()).isNull();
            assertThat(response.campaignId()).isNull();
        }

        @Test
        void createWithCampaignOfOrganization_checksItAndStoresIt() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            when(relationRepository.save(any(Relation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RelationDetailResponse response = service.create(organizationId, user,
                    createRequestWithCampaign(campaignId));

            // Assert
            verify(campaignQueryService).ensureExists(organizationId, campaignId);
            Relation saved = captureSaved();
            assertThat(saved.getCampaignId()).isEqualTo(campaignId);
            assertThat(saved.getSource()).isEqualTo(RelationSource.CAMPAIGN);
            assertThat(response.campaignId()).isEqualTo(campaignId);
        }

        @Test
        void createWithForeignCampaign_throwsNotFoundAndSavesNothing() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            doThrow(campaignNotFound(campaignId)).when(campaignQueryService).ensureExists(organizationId, campaignId);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequestWithCampaign(campaignId)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND.getCode()));
            verify(relationRepository, never()).save(any());
        }

        @Test
        void updateWithNullCampaign_skipsTheCheckAndClearsTheLink() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            relation.setCampaignId(UUID.randomUUID());
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            service.update(organizationId, relation.getId(), updateRequestWithCampaign(null));

            // Assert
            verify(campaignQueryService, never()).ensureExists(any(), any());
            assertThat(relation.getCampaignId()).isNull();
        }

        @Test
        void updateWithCampaignOfOrganization_checksItAndLinks() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            service.update(organizationId, relation.getId(), updateRequestWithCampaign(campaignId));

            // Assert
            verify(campaignQueryService).ensureExists(organizationId, campaignId);
            assertThat(relation.getCampaignId()).isEqualTo(campaignId);
        }

        @Test
        void updateWithForeignCampaign_throwsNotFoundSavesNothingAndKeepsTheLink() {
            // Arrange
            UUID campaignId = UUID.randomUUID();
            UUID currentCampaignId = UUID.randomUUID();
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            relation.setCampaignId(currentCampaignId);
            doThrow(campaignNotFound(campaignId)).when(campaignQueryService).ensureExists(organizationId, campaignId);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(),
                    updateRequestWithCampaign(campaignId))).isInstanceOf(NotFoundException.class);
            verify(relationRepository, never()).saveAndFlush(any());
            assertThat(relation.getCampaignId()).isEqualTo(currentCampaignId);
        }
    }

    @Nested
    class Update {

        @Test
        void sameOwner_skipsMembershipCheckAndKeepsTypeAndStage() {
            // Arrange: an owner who left the organization does not block editing the rest
            UUID ownerUserId = UUID.randomUUID();
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.WARM, ownerUserId);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            RelationDetailResponse response = service.update(organizationId, relation.getId(),
                    updateRequest(ownerUserId));

            // Assert
            verify(organizationMemberQueryService, never()).findMember(any(), any());
            verify(appAccessValidator, never()).ensureUsersHaveAppAccess(any(), any(), any());
            assertThat(relation.getName()).isEqualTo("Fabrikam B.V.");
            assertThat(relation.getType()).isEqualTo(RelationType.LEAD);
            assertThat(relation.getStage()).isEqualTo(PipelineStage.WARM);
            assertThat(relation.getOrganizationId()).isEqualTo(organizationId);
            assertThat(response.notes()).isEqualTo("Met at the fair");
        }

        @Test
        void newOwnerNotMember_throwsConflictAndSavesNothing() {
            // Arrange
            Relation relation = storedRelation(RelationType.CUSTOMER, null, null);
            UUID ownerUserId = UUID.randomUUID();
            when(organizationMemberQueryService.findMember(organizationId, ownerUserId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), updateRequest(ownerUserId)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_STA_001"));
            verify(relationRepository, never()).saveAndFlush(any());
        }

        @Test
        void newOwnerWithoutOtak_throwsMemberWithoutAppAccessAndSavesNothing() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            UUID ownerUserId = UUID.randomUUID();
            stubOwnerWithoutOtak(ownerUserId);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relation.getId(), updateRequest(ownerUserId)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("APP_VAL_002"));
            assertThat(relation.getOwnerUserId()).isNull();
            verify(relationRepository, never()).saveAndFlush(any());
        }

        @Test
        void relationOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID relationId = UUID.randomUUID();
            when(relationRepository.findByIdAndOrganizationId(relationId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, relationId, updateRequest(null)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_RELATION_FOUND.getCode()));
            verify(relationRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class ChangeStage {

        @Test
        void lead_movesToNewStage() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.NEW, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            RelationDetailResponse response = service.changeStage(organizationId, relation.getId(), PipelineStage.PROPOSAL);

            // Assert
            assertThat(relation.getStage()).isEqualTo(PipelineStage.PROPOSAL);
            assertThat(response.stage()).isEqualTo(PipelineStage.PROPOSAL);
        }

        @Test
        void closed_turnsLeadIntoCustomerAtClosedInOneSave() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.PROPOSAL, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            RelationDetailResponse response = service.changeStage(organizationId, relation.getId(), PipelineStage.CLOSED);

            // Assert
            assertThat(relation.getType()).isEqualTo(RelationType.CUSTOMER);
            assertThat(relation.getStage()).isEqualTo(PipelineStage.CLOSED);
            assertThat(response.type()).isEqualTo(RelationType.CUSTOMER);
            assertThat(response.stage()).isEqualTo(PipelineStage.CLOSED);
            verify(relationRepository, times(1)).saveAndFlush(any(Relation.class));
        }

        @Test
        void directOnLead_throwsStageNotForTypeAndSavesNothing() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.WARM, null);

            // Act and assert
            assertThatThrownBy(() -> service.changeStage(organizationId, relation.getId(), PipelineStage.DIRECT))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_STA_006"));
            assertThat(relation.getType()).isEqualTo(RelationType.LEAD);
            assertThat(relation.getStage()).isEqualTo(PipelineStage.WARM);
            verify(relationRepository, never()).saveAndFlush(any());
        }

        @Test
        void directOnCustomer_throwsRelationNotLeadAndSavesNothing() {
            // Arrange
            Relation relation = storedRelation(RelationType.CUSTOMER, PipelineStage.CLOSED, null);

            // Act and assert
            assertThatThrownBy(() -> service.changeStage(organizationId, relation.getId(), PipelineStage.DIRECT))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_STA_002"));
            verify(relationRepository, never()).saveAndFlush(any());
        }

        @Test
        void customer_throwsRelationNotLeadAndSavesNothing() {
            // Arrange
            Relation relation = storedRelation(RelationType.CUSTOMER, PipelineStage.DIRECT, null);

            // Act and assert
            assertThatThrownBy(() -> service.changeStage(organizationId, relation.getId(), PipelineStage.WARM))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_RELATION_NOT_LEAD.getCode()));
            assertThat(relation.getStage()).isEqualTo(PipelineStage.DIRECT);
            verify(relationRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class ChangeType {

        @Test
        void leadToCustomer_setsStageClosed() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.PROPOSAL, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            RelationDetailResponse response = service.changeType(organizationId, user, relation.getId(),
                    RelationType.CUSTOMER);

            // Assert
            assertThat(relation.getType()).isEqualTo(RelationType.CUSTOMER);
            assertThat(relation.getStage()).isEqualTo(PipelineStage.CLOSED);
            assertThat(response.type()).isEqualTo(RelationType.CUSTOMER);
            assertThat(response.stage()).isEqualTo(PipelineStage.CLOSED);
        }

        @Test
        void customerToLead_startsAtStageNew() {
            // Arrange
            Relation relation = storedRelation(RelationType.CUSTOMER, PipelineStage.CLOSED, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            RelationDetailResponse response = service.changeType(organizationId, user, relation.getId(),
                    RelationType.LEAD);

            // Assert
            assertThat(relation.getStage()).isEqualTo(PipelineStage.NEW);
            assertThat(response.stage()).isEqualTo(PipelineStage.NEW);
        }

        @Test
        void customerToSupplier_clearsStage() {
            // Arrange
            Relation relation = storedRelation(RelationType.CUSTOMER, PipelineStage.DIRECT, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            service.changeType(organizationId, user, relation.getId(), RelationType.SUPPLIER);

            // Assert
            assertThat(relation.getStage()).isNull();
        }

        @Test
        void supplierToCustomer_setsStageDirect() {
            // Arrange
            Relation relation = storedRelation(RelationType.SUPPLIER, null, null);
            when(relationRepository.saveAndFlush(relation)).thenReturn(relation);

            // Act
            service.changeType(organizationId, user, relation.getId(), RelationType.CUSTOMER);

            // Assert
            assertThat(relation.getStage()).isEqualTo(PipelineStage.DIRECT);
        }

        @Test
        void refusedByValidator_writesNothing() {
            // Arrange: the same type is refused by the validator, whose error code its own test pins
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.WARM, null);

            // Act and assert
            assertThatThrownBy(() -> service.changeType(organizationId, user, relation.getId(), RelationType.LEAD))
                    .isInstanceOf(ConflictException.class);
            assertThat(relation.getStage()).isEqualTo(PipelineStage.WARM);
            verify(relationRepository, never()).saveAndFlush(any());
        }

        @Test
        void relationOfAnotherOrganization_throwsNotFoundAndWritesNothing() {
            // Arrange
            UUID relationId = UUID.randomUUID();
            when(relationRepository.findByIdAndOrganizationId(relationId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.changeType(organizationId, user, relationId, RelationType.CUSTOMER))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo("OTK_NOT_001"));
            verify(relationRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void relationOfOrganization_isDeletedThenItsDocumentsAndAttachmentsAndThoseOfItsMails() {
            // Arrange
            Relation relation = storedRelation(RelationType.LEAD, PipelineStage.LOST, null);
            UUID mailId = UUID.randomUUID();
            when(mailQueryService.getIdsByRelation(organizationId, relation.getId())).thenReturn(List.of(mailId));

            // Act
            service.delete(organizationId, relation.getId());

            // Assert
            InOrder order = inOrder(mailQueryService, relationRepository, documentService, attachmentService);
            order.verify(mailQueryService).getIdsByRelation(organizationId, relation.getId());
            order.verify(relationRepository).delete(relation);
            order.verify(relationRepository).flush();
            order.verify(documentService).deleteDocumentsByEntity(DocumentEntityType.RELATION, relation.getId());
            order.verify(attachmentService).deleteAttachmentsByEntity(StorageEntityType.RELATION, relation.getId());
            order.verify(attachmentService).deleteAttachmentsByEntity(StorageEntityType.MAIL, mailId);
        }

        @Test
        void relationOfAnotherOrganization_throwsNotFoundAndDeletesNothing() {
            // Arrange
            UUID relationId = UUID.randomUUID();
            when(relationRepository.findByIdAndOrganizationId(relationId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, relationId))
                    .isInstanceOf(NotFoundException.class);
            verify(relationRepository, never()).delete(any(Relation.class));
            verify(attachmentService, never()).deleteAttachmentsByEntity(any(), any());
            verify(documentService, never()).deleteDocumentsByEntity(any(), any());
        }
    }
}
