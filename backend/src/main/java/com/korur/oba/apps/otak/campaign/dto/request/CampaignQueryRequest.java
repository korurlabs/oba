package com.korur.oba.apps.otak.campaign.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.campaign.constant.CampaignConstant;
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Filters of the campaign list. The list is not paged, because an organization keeps tens to
 * hundreds of campaigns and the page, the pickers of outreaches and relations and the CLI all
 * read it whole. With status SCHEDULED it reads as an agenda, soonest planned first;
 * otherwise the most recently changed campaign comes first.
 */
@Schema(description = "Filters of the campaign list")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CampaignQueryRequest {

    @Schema(description = "Only campaigns with this status; null for every status", nullable = true,
            example = "SCHEDULED")
    private CampaignStatus status;

    @Schema(description = "Only campaigns on this channel; null for every channel", nullable = true,
            example = "LINKEDIN")
    private CampaignChannel channel;

    @Schema(description = "Part of the name, case ignored; absent for every campaign", nullable = true,
            example = "igef")
    @NullOrNotBlank(message = CampaignConstant.SEARCH_BLANK_MESSAGE)
    @Size(max = CampaignConstant.SEARCH_MAX_LENGTH, message = CampaignConstant.SEARCH_SIZE_MESSAGE)
    private String search;
}
