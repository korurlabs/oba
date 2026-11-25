package com.korur.oba.apps.otak.campaign.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.dto.projection.CampaignContentLanguageProjection;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignQueryRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.apps.otak.campaign.helper.CampaignOwnerHelper;
import com.korur.oba.apps.otak.campaign.mapper.CampaignMapperImpl;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.campaign.repository.CampaignRepository;
import com.korur.oba.apps.otak.campaign.validator.CampaignValidator;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class CampaignQueryServiceImplUnitTest {

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
    private LoggingService loggingService;

    private CampaignQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        CampaignValidator validator = new CampaignValidator(campaignRepository, organizationMemberQueryService,
                appAccessValidator, loggingService);
        service = new CampaignQueryServiceImpl(campaignRepository, campaignContentRepository, new CampaignMapperImpl(),
                validator, new CampaignOwnerHelper(userQueryService), loggingService);
    }

    private Campaign campaign(String name, UUID ownerUserId) {
        Campaign campaign = Campaign.builder().name(name).channel(CampaignChannel.LINKEDIN)
                .status(CampaignStatus.DRAFT).ownerUserId(ownerUserId).organizationId(organizationId).build();
        campaign.setId(UUID.randomUUID());
        return campaign;
    }

    private static CampaignContentLanguageProjection language(UUID campaignId, DocumentLanguage language) {
        return new CampaignContentLanguageProjection() {
            @Override
            public UUID getCampaignId() {
                return campaignId;
            }

            @Override
            public DocumentLanguage getLanguage() {
                return language;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Sort listAndCaptureSort(CampaignQueryRequest request) {
        when(campaignRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        service.list(organizationId, request);
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(campaignRepository).findAll(any(Specification.class), sort.capture());
        return sort.getValue();
    }

    @Test
    void list_scheduled_sortsSoonestPlannedFirst() {
        // Arrange
        CampaignQueryRequest request = new CampaignQueryRequest();
        request.setStatus(CampaignStatus.SCHEDULED);

        // Act
        Sort sort = listAndCaptureSort(request);

        // Assert
        assertThat(sort.getOrderFor(CampaignConstant.FIELD_PLANNED_AT).getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor(CampaignConstant.FIELD_UPDATED_AT)).isNull();
    }

    @Test
    void list_otherStatusOrNone_sortsMostRecentlyChangedFirst() {
        // Arrange
        CampaignQueryRequest request = new CampaignQueryRequest();
        request.setStatus(CampaignStatus.PUBLISHED);

        // Act
        Sort sort = listAndCaptureSort(request);

        // Assert
        assertThat(sort.getOrderFor(CampaignConstant.FIELD_UPDATED_AT).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor(CampaignConstant.FIELD_PLANNED_AT)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_noMatch_returnsEmptyWithoutReadingLanguagesOrOwners() {
        // Arrange
        when(campaignRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        // Act
        List<CampaignListResponse> result = service.list(organizationId, new CampaignQueryRequest());

        // Assert
        assertThat(result).isEmpty();
        verify(campaignContentRepository, never()).findLanguages(anyCollection());
        verify(userQueryService, never()).getUserEntitiesByIds(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_everyMatch_joinsLanguagesAndOwnerNamesInOneReadEach() {
        // Arrange
        UUID ownerUserId = UUID.randomUUID();
        Campaign first = campaign("IGEF", ownerUserId);
        Campaign second = campaign("Newsletter", null);
        when(campaignRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(first, second));
        when(campaignContentRepository.findLanguages(anyCollection())).thenReturn(List.of(
                language(first.getId(), DocumentLanguage.NL), language(first.getId(), DocumentLanguage.EN)));
        User owner = User.builder().email("jane@korur.com").build();
        owner.setId(ownerUserId);
        when(userQueryService.getUserEntitiesByIds(List.of(ownerUserId))).thenReturn(List.of(owner));

        // Act
        List<CampaignListResponse> result = service.list(organizationId, new CampaignQueryRequest());

        // Assert
        assertThat(result).extracting(CampaignListResponse::name).containsExactly("IGEF", "Newsletter");
        assertThat(result.get(0).languages()).containsExactly(DocumentLanguage.NL, DocumentLanguage.EN);
        assertThat(result.get(0).owner().name()).isEqualTo("jane@korur.com");
        assertThat(result.get(1).languages()).isEmpty();
        assertThat(result.get(1).owner()).isNull();
    }

    @Test
    void get_ownCampaign_returnsItWithItsTexts() {
        // Arrange
        Campaign campaign = campaign("IGEF", null);
        CampaignContent text = CampaignContent.builder().campaignId(campaign.getId()).language(DocumentLanguage.TR)
                .text("Merhaba").build();
        when(campaignRepository.findByIdAndOrganizationId(campaign.getId(), organizationId))
                .thenReturn(Optional.of(campaign));
        when(campaignContentRepository.findAllByCampaignIdOrderByLanguageAsc(campaign.getId())).thenReturn(List.of(text));

        // Act
        CampaignDetailResponse result = service.get(organizationId, campaign.getId());

        // Assert
        assertThat(result.id()).isEqualTo(campaign.getId());
        assertThat(result.languages()).containsExactly(DocumentLanguage.TR);
        assertThat(result.contents()).singleElement().satisfies(content -> assertThat(content.text()).isEqualTo("Merhaba"));
    }

    @Test
    void get_campaignOfAnotherOrganization_throwsNotFound() {
        // Arrange
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)).thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.get(organizationId, campaignId))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND.getCode()));
    }

    @Test
    void getEntityById_campaignOfAnyOrganization_returnsIt() {
        // Arrange
        Campaign campaign = campaign("IGEF", null);
        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        // Act and assert
        assertThat(service.getEntityById(campaign.getId())).isSameAs(campaign);
    }
}
