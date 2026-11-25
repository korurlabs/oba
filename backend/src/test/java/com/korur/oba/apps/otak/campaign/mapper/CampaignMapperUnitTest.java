package com.korur.oba.apps.otak.campaign.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignOwnerResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

class CampaignMapperUnitTest {

    private final CampaignMapper mapper = new CampaignMapperImpl();

    @Test
    void toEntity_mapsEveryFieldButTheOrganizationAndTheTexts() {
        // Arrange
        UUID ownerUserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant plannedAt = Instant.parse("2026-12-01T08:30:00Z");
        CampaignCreateRequest request = new CampaignCreateRequest("IGEF", CampaignChannel.X, CampaignStatus.SCHEDULED,
                plannedAt, null, "https://x.com/korur/status/1", ownerUserId, eventId, null,
                List.of(new CampaignContentRequest(DocumentLanguage.EN, "Hello")));

        // Act
        Campaign campaign = mapper.toEntity(request);

        // Assert
        assertThat(campaign.getName()).isEqualTo("IGEF");
        assertThat(campaign.getChannel()).isEqualTo(CampaignChannel.X);
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(campaign.getPlannedAt()).isEqualTo(plannedAt);
        assertThat(campaign.getPostUrl()).isEqualTo("https://x.com/korur/status/1");
        assertThat(campaign.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(campaign.getEventId()).isEqualTo(eventId);
        assertThat(campaign.getOrganizationId()).isNull();
        assertThat(campaign.getId()).isNull();
    }

    @Test
    void toContentEntity_leavesTheCampaignForTheService() {
        CampaignContent content = mapper.toContentEntity(new CampaignContentRequest(DocumentLanguage.TR, "Merhaba"));

        assertThat(content.getLanguage()).isEqualTo(DocumentLanguage.TR);
        assertThat(content.getText()).isEqualTo("Merhaba");
        assertThat(content.getCampaignId()).isNull();
    }

    @Test
    void toListResponse_takesIdAndNameFromTheCampaignNotTheOwner() {
        // Arrange
        Campaign campaign = Campaign.builder().name("IGEF").channel(CampaignChannel.LINKEDIN)
                .status(CampaignStatus.DRAFT).build();
        campaign.setId(UUID.randomUUID());
        CampaignOwnerResponse owner = new CampaignOwnerResponse(UUID.randomUUID(), "Jane Doe");

        // Act
        CampaignListResponse result = mapper.toListResponse(campaign, owner, List.of(DocumentLanguage.NL));

        // Assert
        assertThat(result.id()).isEqualTo(campaign.getId());
        assertThat(result.name()).isEqualTo("IGEF");
        assertThat(result.owner()).isEqualTo(owner);
        assertThat(result.languages()).containsExactly(DocumentLanguage.NL);
    }
}
