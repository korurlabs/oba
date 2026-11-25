package com.korur.oba.apps.kazan.cyberscore.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity holding the live cyberscore of an organization, computed from its
 * {@link Check} rows.
 *
 * One row per organization, which a unique constraint enforces. The row is
 * created on the first scoring change and doubles as the lock that makes the
 * scoring writes of one organization run one after another. Percentage and
 * level are stored, not derived at read time, so the overview and the trend
 * read one row.
 */
@Entity
@Table(name = "cyberscores", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Cyberscore extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Organization the score belongs to, set once on create and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // From 0 to 100; the database checks the range.
    @Builder.Default
    @Column(name = "score_percentage", nullable = false)
    private Integer scorePercentage = 0;

    /**
     * Percentage per category, keyed by the {@link com.korur.oba.apps.kazan.cyberscore.enums.CheckCategory}
     * name. A category without scorable checks has no key.
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breakdown_by_category", columnDefinition = "jsonb", nullable = false)
    private Map<String, Integer> breakdownByCategory = new HashMap<>();

    // Null until the first calculation.
    @Column(name = "last_calculated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = true)
    private Instant lastCalculatedAt;

    // -------------Enums-----------//

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "score_level", nullable = false)
    private CyberscoreLevel scoreLevel = CyberscoreLevel.UNKNOWN;
}
