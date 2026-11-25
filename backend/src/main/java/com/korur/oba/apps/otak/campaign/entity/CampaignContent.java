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
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Entity representing one content of a campaign, a row of otak.campaign_contents: one thing
 * actually shared, for example one LinkedIn post, on one channel, in one language, published
 * by one legal entity of the organization. Translations of one post are contents of the same
 * campaign. A content has no name of its own; it is told apart by channel, language, legal
 * entity and dates.
 *
 * Members post by hand and mark a content published; Oba does not post it. Two check
 * constraints tie the moments to the status: a SCHEDULED content has a planned moment, and a
 * DRAFT or SCHEDULED content has no published moment.
 *
 * Composite foreign keys keep the campaign and the legal entity in the content's
 * organization. The database deletes the contents with their campaign and refuses to delete
 * a legal entity that still publishes a content. Its images are attachments of type
 * CAMPAIGN_CONTENT.
 */
@Entity
@Table(name = "campaign_contents", schema = "otak")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CampaignContent extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Campaign the content belongs to, set once on create.
     */
    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    /**
     * Organization that owns the content, set once on create from its campaign.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Legal entity of the organization that publishes the content.
     */
    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    /**
     * The text, plain text; never blank.
     */
    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    /**
     * Moment the content is planned to go live; required while SCHEDULED.
     */
    @Column(name = "planned_at", nullable = true)
    private Instant plannedAt;

    /**
     * Moment the content went live; empty while DRAFT or SCHEDULED.
     */
    @Column(name = "published_at", nullable = true)
    private Instant publishedAt;

    /**
     * Address of the live post, a full http or https address, or null.
     */
    @Column(name = "post_url", columnDefinition = "TEXT", nullable = true)
    private String postUrl;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "channel", nullable = false)
    private CampaignChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "language", nullable = false)
    private DocumentLanguage language;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private CampaignStatus status;
}
