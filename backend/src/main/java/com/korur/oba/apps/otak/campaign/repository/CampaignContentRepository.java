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
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.dto.projection.CampaignContentLanguageProjection;
import com.korur.oba.apps.otak.campaign.entity.CampaignContent;

/**
 * Repository interface for {@link CampaignContent} entity database operations. A text is
 * always reached through its campaign, which the caller has already read within its
 * organization.
 */
@Repository
public interface CampaignContentRepository
        extends JpaRepository<CampaignContent, UUID>, JpaSpecificationExecutor<CampaignContent> {

    /**
     * Reads the texts of one campaign in the order of the language enum (NL, TR, EN).
     *
     * @param campaignId the campaign
     * @return the texts, at most one per language
     */
    List<CampaignContent> findAllByCampaignIdOrderByLanguageAsc(UUID campaignId);

    /**
     * Reads which languages the given campaigns have a text in, without the texts, for the
     * campaign list. One query serves the whole list.
     *
     * @param campaignIds the campaigns, not empty
     * @return one row per text, ordered by language
     */
    @Query("SELECT c.campaignId AS campaignId, c.language AS language FROM CampaignContent c "
            + "WHERE c.campaignId IN :campaignIds ORDER BY c.language")
    List<CampaignContentLanguageProjection> findLanguages(@Param("campaignIds") Collection<UUID> campaignIds);
}
