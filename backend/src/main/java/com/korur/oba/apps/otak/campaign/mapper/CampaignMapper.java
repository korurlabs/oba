package com.korur.oba.apps.otak.campaign.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignCreateRequest;
import com.korur.oba.apps.otak.campaign.dto.request.CampaignUpdateRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignDetailResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignListResponse;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignOwnerResponse;
import com.korur.oba.apps.otak.campaign.entity.Campaign;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Maps campaign entities to DTOs and requests to entities. The owner, the event and the ICP
 * are plain ids, so the mapper needs no helper; the owner's name is resolved by
 * {@link com.korur.oba.apps.otak.campaign.helper.CampaignOwnerHelper} and the texts are
 * written by the service, which passes both in.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CampaignMapper {

    /**
     * Maps a create request to a new entity. The service sets the organization from the
     * checked request header, the default status and the published moment, and writes the
     * texts as rows of their own.
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
     * Maps a text of a request to a new row; the service sets its campaign.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "campaignId", ignore = true)
    CampaignContent toContentEntity(CampaignContentRequest request);

    /**
     * Maps a stored text to its response.
     */
    CampaignContentResponse toContentResponse(CampaignContent content);

    /**
     * Maps the stored texts of a campaign to their responses, keeping their order.
     */
    List<CampaignContentResponse> toContentResponses(List<CampaignContent> contents);

    /**
     * Maps a campaign, its resolved owner and the languages of its texts to a list item.
     *
     * @param campaign  the stored campaign
     * @param owner     the resolved owner, or null when the campaign has none
     * @param languages the languages the campaign has a text in
     * @return the list item
     */
    @Mapping(target = "id", source = "campaign.id")
    @Mapping(target = "name", source = "campaign.name")
    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "languages", source = "languages")
    CampaignListResponse toListResponse(Campaign campaign, CampaignOwnerResponse owner,
                                        List<DocumentLanguage> languages);

    /**
     * Maps a campaign, its resolved owner and its texts to the detail response.
     *
     * @param campaign  the stored campaign
     * @param owner     the resolved owner, or null when the campaign has none
     * @param languages the languages of the texts, in their order
     * @param contents  the texts
     * @return the detail response
     */
    @Mapping(target = "id", source = "campaign.id")
    @Mapping(target = "name", source = "campaign.name")
    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "languages", source = "languages")
    @Mapping(target = "contents", source = "contents")
    CampaignDetailResponse toDetailResponse(Campaign campaign, CampaignOwnerResponse owner,
                                            List<DocumentLanguage> languages, List<CampaignContentResponse> contents);
}
