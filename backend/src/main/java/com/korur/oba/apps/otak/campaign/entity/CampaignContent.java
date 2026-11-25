package com.korur.oba.apps.otak.campaign.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Entity representing the text of a campaign in one language, a row of
 * otak.campaign_contents. A campaign has at most one text per language, which a unique
 * index on the campaign and the language enforces, and its texts are written together with
 * the campaign. The database deletes them with their campaign.
 */
@Entity
@Table(
        name = "campaign_contents",
        schema = "otak",
        uniqueConstraints = @UniqueConstraint(
                name = "campaign_contents_campaign_id_language_uk",
                columnNames = {"campaign_id", "language"}))
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CampaignContent extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Campaign the text belongs to, set once on create.
     */
    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    /**
     * The text, plain text of at most 20,000 characters; never blank.
     */
    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    // -------------Enums-----------//

    /**
     * Language of the text; fixed once written, since a text in another language is another row.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "language", nullable = false, updatable = false)
    private DocumentLanguage language;
}
