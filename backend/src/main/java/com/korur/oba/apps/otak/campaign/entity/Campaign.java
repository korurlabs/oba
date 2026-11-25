package com.korur.oba.apps.otak.campaign.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing a campaign, a row of otak.campaigns: the umbrella of an organization
 * over the posts and mailings that belong together, for example one post and its
 * translations. What is actually shared lives in its {@link CampaignContent} rows.
 *
 * Every campaign belongs to one organization and is only read and written within it. Its
 * name is unique per organization with case ignored, which a unique index on the
 * lower-case name enforces. The dates are calendar days without a timezone, and the end
 * date is never before the start date, which a check constraint enforces.
 *
 * The owner, the event and the ICP are plain ids, as on a relation, and the database clears
 * each one when its row is deleted. Deleting a campaign deletes its contents and clears the
 * campaign link of its outreaches and relations, which stay.
 */
@Entity
@Table(
        name = "campaigns",
        schema = "otak",
        uniqueConstraints = @UniqueConstraint(
                name = "campaigns_organization_id_lower_name_uk",
                columnNames = {"organization_id", "name"}))
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Campaign extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Name shown in the campaign list, stored trimmed; unique per organization, case ignored.
     */
    @Column(name = "name", columnDefinition = "TEXT", nullable = false)
    private String name;

    /**
     * What the campaign is about, plain text, or null.
     */
    @Column(name = "description", columnDefinition = "TEXT", nullable = true)
    private String description;

    /**
     * First day of the campaign, or null.
     */
    @Column(name = "start_date", nullable = true)
    private LocalDate startDate;

    /**
     * Last day of the campaign, or null; never before the start date.
     */
    @Column(name = "end_date", nullable = true)
    private LocalDate endDate;

    /**
     * Member who looks after the campaign, or null.
     */
    @Column(name = "owner_user_id", nullable = true)
    private UUID ownerUserId;

    /**
     * Event of the same organization the campaign belongs to, or null.
     */
    @Column(name = "event_id", nullable = true)
    private UUID eventId;

    /**
     * ICP of the same organization the campaign is aimed at, or null.
     */
    @Column(name = "icp_id", nullable = true)
    private UUID icpId;

    /**
     * Organization that owns the campaign, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
}
