package com.korur.oba.apps.otak.campaign.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.campaign.enums.CampaignChannel;
import com.korur.oba.apps.otak.campaign.enums.CampaignStatus;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing a campaign, a row of otak.campaigns: one post or mailing of an
 * organization on one channel, planned and then posted by hand by a member. Oba does not
 * post it; a member marks it published once it is live.
 *
 * Every campaign belongs to one organization and is only read and written within it. Its
 * name is unique per organization with case ignored, which a unique index on the
 * lower-case name enforces. Two check constraints tie the moments to the status: a
 * SCHEDULED campaign has a planned moment, and a DRAFT or SCHEDULED campaign has no
 * published moment.
 *
 * The owner, the event and the ICP are plain ids, as on a relation, and the database clears
 * each one when its row is deleted. The texts per language are rows of
 * {@link CampaignContent}, deleted with the campaign. Deleting a campaign clears the
 * campaign link of its outreaches and relations and keeps them.
 */
@Entity
@Table(name = "campaigns", schema = "otak")
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
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /**
     * Moment the campaign is planned to go live; required while SCHEDULED.
     */
    @Column(name = "planned_at", nullable = true)
    private Instant plannedAt;

    /**
     * Moment the campaign went live; empty while DRAFT or SCHEDULED.
     */
    @Column(name = "published_at", nullable = true)
    private Instant publishedAt;

    /**
     * Address of the live post, a full http or https address, or null.
     */
    @Column(name = "post_url", length = 500, nullable = true)
    private String postUrl;

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

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "channel", nullable = false)
    private CampaignChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private CampaignStatus status;
}
