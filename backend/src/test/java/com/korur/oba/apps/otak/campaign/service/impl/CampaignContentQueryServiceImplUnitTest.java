package com.korur.oba.apps.otak.campaign.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.mapper.CampaignContentMapperImpl;
import com.korur.oba.apps.otak.campaign.repository.CampaignContentRepository;
import com.korur.oba.apps.otak.campaign.repository.CampaignRepository;
import com.korur.oba.apps.otak.campaign.validator.CampaignContentValidator;
import com.korur.oba.apps.otak.campaign.validator.CampaignValidator;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.service.LegalEntityQueryService;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class CampaignContentQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();

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
    private LoggingService loggingService;

    private CampaignContentQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        CampaignValidator campaignValidator = new CampaignValidator(campaignRepository, organizationMemberQueryService,
                appAccessValidator, loggingService);
        CampaignContentValidator contentValidator = new CampaignContentValidator(campaignContentRepository,
                Clock.systemUTC(), loggingService);
        service = new CampaignContentQueryServiceImpl(campaignContentRepository, new CampaignContentMapperImpl(),
                contentValidator, campaignValidator, legalEntityQueryService, loggingService);
    }

    private void campaignStored() {
        Campaign campaign = Campaign.builder().name("De wacht").organizationId(organizationId).build();
        campaign.setId(campaignId);
        when(campaignRepository.findByIdAndOrganizationId(campaignId, organizationId))
                .thenReturn(Optional.of(campaign));
    }

    private CampaignContent content(UUID legalEntityId, String text) {
        CampaignContent content = CampaignContent.builder().campaignId(campaignId).organizationId(organizationId)
                .legalEntityId(legalEntityId).text(text).build();
        content.setId(UUID.randomUUID());
        return content;
    }

    private void legalEntity(UUID legalEntityId, String shortName) {
        LegalEntity legalEntity = LegalEntity.builder().shortName(shortName).build();
        legalEntity.setId(legalEntityId);
        when(legalEntityQueryService.getEntity(organizationId, legalEntityId)).thenReturn(legalEntity);
    }

    @Test
    void list_agendaOrder_isKeptAndEachLegalEntityIsReadOnce() {
        // Arrange
        campaignStored();
        UUID korur = UUID.randomUUID();
        UUID tayga = UUID.randomUUID();
        when(campaignContentRepository.findAgenda(campaignId, organizationId)).thenReturn(List.of(
                content(korur, "First"), content(tayga, "Second"), content(korur, "Third")));
        legalEntity(korur, "Korur B.V.");
        legalEntity(tayga, "Tayga");

        // Act
        List<CampaignContentResponse> result = service.list(organizationId, campaignId);

        // Assert
        assertThat(result).extracting(CampaignContentResponse::text).containsExactly("First", "Second", "Third");
        assertThat(result).extracting(CampaignContentResponse::legalEntityShortName)
                .containsExactly("Korur B.V.", "Tayga", "Korur B.V.");
        verify(legalEntityQueryService, times(1)).getEntity(organizationId, korur);
    }

    @Test
    void list_campaignOfAnotherOrganization_throwsNotFoundWithoutReadingContents() {
        // Arrange
        when(campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)).thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.list(organizationId, campaignId))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.NOT_CAMPAIGN_FOUND.getCode()));
        verify(campaignContentRepository, never()).findAgenda(any(), any());
    }

    @Test
    void getEntityById_contentOfAnyOrganization_returnsIt() {
        // Arrange
        CampaignContent content = content(UUID.randomUUID(), "Hallo");
        when(campaignContentRepository.findById(content.getId())).thenReturn(Optional.of(content));

        // Act and assert
        assertThat(service.getEntityById(content.getId())).isSameAs(content);
    }

    @Test
    void existsByLegalEntity_contentNamesIt_returnsTrue() {
        // Arrange
        UUID legalEntityId = UUID.randomUUID();
        when(campaignContentRepository.existsByOrganizationIdAndLegalEntityId(organizationId, legalEntityId))
                .thenReturn(true);

        // Act and assert
        assertThat(service.existsByLegalEntity(organizationId, legalEntityId)).isTrue();
    }
}
