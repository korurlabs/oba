package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Filters of the campaign list. The list is not paged, because an organization keeps tens to
 * hundreds of campaigns and the page, the pickers of outreaches and relations and the CLI all
 * read it whole. The most recently changed campaign comes first.
 */
@Schema(description = "Filters of the campaign list")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CampaignQueryRequest {

    @Schema(description = "Part of the name, case ignored; absent for every campaign", nullable = true,
            example = "wacht")
    @NullOrNotBlank(message = CampaignConstant.SEARCH_BLANK_MESSAGE)
    private String search;

    @Schema(description = "Only campaigns of this event; absent for every campaign", nullable = true,
            example = "8a1d3c5e-2b4f-4e6a-9c7d-1f2e3a4b5c6d")
    private UUID eventId;
}
