package com.korur.oba.apps.otak.campaign.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignOwnerResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;

/**
 * Maps campaign entities to DTOs and requests to entities. The owner, the event and the ICP
 * are plain ids, so the mapper needs no helper; the owner's name is resolved by
 * {@link com.korur.oba.apps.otak.campaign.helper.CampaignOwnerHelper} and the number of
 * contents is counted by the service, which passes both in.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CampaignMapper {

    /**
     * Maps a create request to a new entity. The service sets the organization from the
     * checked request header and the trimmed name.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    Campaign toEntity(CampaignCreateRequest request);

    /**
     * Writes every field of an update request onto the campaign; null clears an optional
     * field, so nothing stored survives a field the client left out.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    void updateEntity(@MappingTarget Campaign campaign, CampaignUpdateRequest request);

    /**
     * Maps a campaign, its resolved owner and the number of its contents to a list item.
     *
     * @param campaign     the stored campaign
     * @param owner        the resolved owner, or null when the campaign has none
     * @param contentCount the number of contents of the campaign
     * @return the list item
     */
    @Mapping(target = "id", source = "campaign.id")
    @Mapping(target = "name", source = "campaign.name")
    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "contentCount", source = "contentCount")
    CampaignListResponse toListResponse(Campaign campaign, CampaignOwnerResponse owner, long contentCount);

    /**
     * Maps a campaign, its resolved owner and the number of its contents to the detail response.
     *
     * @param campaign     the stored campaign
     * @param owner        the resolved owner, or null when the campaign has none
     * @param contentCount the number of contents of the campaign
     * @return the detail response
     */
    @Mapping(target = "id", source = "campaign.id")
    @Mapping(target = "name", source = "campaign.name")
    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "contentCount", source = "contentCount")
    CampaignDetailResponse toDetailResponse(Campaign campaign, CampaignOwnerResponse owner, long contentCount);
}
