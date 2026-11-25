package com.korur.oba.apps.otak.campaign.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

class CampaignContentMapperUnitTest {

    private final CampaignContentMapper mapper = new CampaignContentMapperImpl();

    @Test
    void toEntity_leavesCampaignOrganizationStatusAndPublishedMomentToTheService() {
        // Arrange
        UUID legalEntityId = UUID.randomUUID();
        Instant plannedAt = Instant.parse("2026-12-01T08:30:00Z");
        CampaignContentRequest request = new CampaignContentRequest(legalEntityId, CampaignChannel.X,
                DocumentLanguage.TR, "Merhaba", CampaignStatus.PUBLISHED, plannedAt,
                Instant.parse("2026-11-01T08:30:00Z"), "https://x.com/korur/status/1");

        // Act
        CampaignContent content = mapper.toEntity(request);

        // Assert
        assertThat(content.getLegalEntityId()).isEqualTo(legalEntityId);
        assertThat(content.getChannel()).isEqualTo(CampaignChannel.X);
        assertThat(content.getLanguage()).isEqualTo(DocumentLanguage.TR);
        assertThat(content.getText()).isEqualTo("Merhaba");
        assertThat(content.getPlannedAt()).isEqualTo(plannedAt);
        assertThat(content.getPostUrl()).isEqualTo("https://x.com/korur/status/1");
        assertThat(content.getStatus()).isNull();
        assertThat(content.getPublishedAt()).isNull();
        assertThat(content.getCampaignId()).isNull();
        assertThat(content.getOrganizationId()).isNull();
    }

    @Test
    void toResponse_takesTheIdFromTheContentAndTheShortNameAsGiven() {
        // Arrange
        CampaignContent content = CampaignContent.builder().campaignId(UUID.randomUUID())
                .legalEntityId(UUID.randomUUID()).channel(CampaignChannel.LINKEDIN).language(DocumentLanguage.NL)
                .text("Hallo").status(CampaignStatus.DRAFT).build();
        content.setId(UUID.randomUUID());

        // Act
        CampaignContentResponse response = mapper.toResponse(content, "Korur B.V.");

        // Assert
        assertThat(response.id()).isEqualTo(content.getId());
        assertThat(response.campaignId()).isEqualTo(content.getCampaignId());
        assertThat(response.legalEntityShortName()).isEqualTo("Korur B.V.");
        assertThat(response.status()).isEqualTo(CampaignStatus.DRAFT);
    }
}
