package com.korur.oba.apps.otak.campaign.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.entity.Campaign;

/**
 * Repository interface for {@link Campaign} entity database operations.
 */
@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID>, JpaSpecificationExecutor<Campaign> {

    /**
     * Reads one campaign within an organization, so a campaign of another organization is not found.
     *
     * @param id             the campaign identifier
     * @param organizationId the organization that owns the campaign
     * @return the campaign, or empty when the organization has no campaign with the id
     */
    Optional<Campaign> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Tells whether the organization already has a campaign with the name, case ignored.
     * Used on create, where no campaign is excluded.
     *
     * @param organizationId the organization that owns the campaigns
     * @param name           the name to look for
     * @return true when a campaign of the organization has the name
     */
    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    /**
     * Tells whether another campaign of the organization has the name, case ignored. Used on
     * update, where the campaign being changed may keep its own name.
     *
     * @param organizationId the organization that owns the campaigns
     * @param name           the name to look for
     * @param id             the campaign left out of the check
     * @return true when a campaign of the organization other than the given one has the name
     */
    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(UUID organizationId, String name, UUID id);

    /**
     * Sets the last-change time of one campaign, in one statement. A change to the texts alone
     * writes no campaign column, so auditing would leave the time as it was and the campaign
     * would not rise in the list sorted by newest change.
     *
     * The persistence context is flushed before and cleared after the statement, so a caller
     * reads the campaign again to see the new time.
     *
     * @param id        the campaign identifier
     * @param updatedAt the new last-change time
     * @return the number of rows changed, 1 for an existing campaign
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Campaign c
            SET c.updatedAt = :updatedAt
            WHERE c.id = :id
            """)
    int touchUpdatedAt(@Param("id") UUID id, @Param("updatedAt") Instant updatedAt);
}
