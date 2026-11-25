package com.korur.oba.apps.otak.campaign.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.request.CampaignContentRequest;
import com.korur.oba.apps.otak.campaign.dto.response.CampaignContentResponse;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;

/**
 * Maps campaign contents to DTOs and requests to entities. The campaign, the organization,
 * the status and the published moment are set by the service from the checked campaign and
 * the status rules; the legal entity's short name is read by the service and passed in.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CampaignContentMapper {

    /**
     * Maps a request to a new content. The service sets the campaign, the organization, the
     * default status and the published moment.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "campaignId", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    CampaignContent toEntity(CampaignContentRequest request);

    /**
     * Writes every field of a request onto the content; null clears an optional field. The
     * service sets the status and the published moment.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "campaignId", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    void updateEntity(@MappingTarget CampaignContent content, CampaignContentRequest request);

    /**
     * Maps a stored content and the short name of its legal entity to the response.
     *
     * @param content              the stored content
     * @param legalEntityShortName the short name of the content's legal entity
     * @return the response
     */
    @Mapping(target = "id", source = "content.id")
    @Mapping(target = "legalEntityShortName", source = "legalEntityShortName")
    CampaignContentResponse toResponse(CampaignContent content, String legalEntityShortName);
}
