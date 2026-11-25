package com.korur.oba.apps.otak.campaign.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.projection.CampaignContentCountProjection;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;

/**
 * Repository interface for {@link CampaignContent} entity database operations. A content is
 * read through its campaign and organization, so a content of another campaign or
 * organization is not found.
 */
@Repository
public interface CampaignContentRepository
        extends JpaRepository<CampaignContent, UUID>, JpaSpecificationExecutor<CampaignContent> {

    /**
     * Reads one content of a campaign of the organization.
     *
     * @param id             the content identifier
     * @param campaignId     the campaign the content must belong to
     * @param organizationId the organization that owns the campaign
     * @return the content, or empty when the campaign of the organization has no content with the id
     */
    Optional<CampaignContent> findByIdAndCampaignIdAndOrganizationId(UUID id, UUID campaignId, UUID organizationId);

    /**
     * Reads every content of a campaign of the organization as an agenda: the soonest planned
     * first, the ones without a planned moment last, then the most recently changed, with the
     * id as tie-breaker so the order is stable.
     *
     * @param campaignId     the campaign
     * @param organizationId the organization that owns the campaign
     * @return the contents, empty when the campaign has none
     */
    @Query("""
            SELECT c FROM CampaignContent c
            WHERE c.campaignId = :campaignId AND c.organizationId = :organizationId
            ORDER BY c.plannedAt ASC NULLS LAST, c.updatedAt DESC, c.id DESC
            """)
    List<CampaignContent> findAgenda(@Param("campaignId") UUID campaignId,
                                     @Param("organizationId") UUID organizationId);

    /**
     * Reads the ids of every content of a campaign, so their attachments can be removed once
     * the campaign is deleted.
     *
     * @param campaignId the campaign
     * @return the content ids, empty when the campaign has none
     */
    @Query("SELECT c.id FROM CampaignContent c WHERE c.campaignId = :campaignId")
    List<UUID> findIdsByCampaignId(@Param("campaignId") UUID campaignId);

    /**
     * Counts the contents of the given campaigns in one grouped query, for the campaign list.
     * A campaign without contents has no row.
     *
     * @param campaignIds the campaigns, not empty
     * @return one row per campaign that has contents
     */
    @Query("""
            SELECT c.campaignId AS campaignId, COUNT(c) AS contentCount FROM CampaignContent c
            WHERE c.campaignId IN :campaignIds
            GROUP BY c.campaignId
            """)
    List<CampaignContentCountProjection> countByCampaignIds(@Param("campaignIds") Collection<UUID> campaignIds);

    /**
     * Counts the contents of one campaign.
     *
     * @param campaignId the campaign
     * @return the number of contents
     */
    long countByCampaignId(UUID campaignId);
}
