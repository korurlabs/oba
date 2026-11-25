package com.korur.oba.apps.otak.campaign.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignOwnerResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;

class CampaignMapperUnitTest {

    private final CampaignMapper mapper = new CampaignMapperImpl();

    @Test
    void toEntity_mapsEveryFieldButTheOrganization() {
        // Arrange
        UUID ownerUserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID icpId = UUID.randomUUID();
        CampaignCreateRequest request = new CampaignCreateRequest("De wacht", "Night watch", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31), ownerUserId, eventId, icpId);

        // Act
        Campaign campaign = mapper.toEntity(request);

        // Assert
        assertThat(campaign.getName()).isEqualTo("De wacht");
        assertThat(campaign.getDescription()).isEqualTo("Night watch");
        assertThat(campaign.getStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(campaign.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(campaign.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(campaign.getEventId()).isEqualTo(eventId);
        assertThat(campaign.getIcpId()).isEqualTo(icpId);
        assertThat(campaign.getOrganizationId()).isNull();
        assertThat(campaign.getId()).isNull();
    }

    @Test
    void updateEntity_clearsOptionalFieldsSentAsNull() {
        // Arrange
        UUID organizationId = UUID.randomUUID();
        Campaign campaign = Campaign.builder().name("Old").description("Old text").startDate(LocalDate.of(2026, 1, 1))
                .eventId(UUID.randomUUID()).organizationId(organizationId).build();

        // Act
        mapper.updateEntity(campaign, new CampaignUpdateRequest("New", null, null, null, null, null, null));

        // Assert
        assertThat(campaign.getName()).isEqualTo("New");
        assertThat(campaign.getDescription()).isNull();
        assertThat(campaign.getStartDate()).isNull();
        assertThat(campaign.getEventId()).isNull();
        assertThat(campaign.getOrganizationId()).isEqualTo(organizationId);
    }

    @Test
    void toListResponse_takesIdAndNameFromTheCampaignNotTheOwner() {
        // Arrange
        Campaign campaign = Campaign.builder().name("De wacht").build();
        campaign.setId(UUID.randomUUID());
        CampaignOwnerResponse owner = new CampaignOwnerResponse(UUID.randomUUID(), "Jane Doe");

        // Act
        CampaignListResponse result = mapper.toListResponse(campaign, owner, 3L);

        // Assert
        assertThat(result.id()).isEqualTo(campaign.getId());
        assertThat(result.name()).isEqualTo("De wacht");
        assertThat(result.owner()).isEqualTo(owner);
        assertThat(result.contentCount()).isEqualTo(3L);
    }
}
