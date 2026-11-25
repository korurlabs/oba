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
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckStatus;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing one security check of an organization.
 *
 * Weight, category and outcome feed the organization's {@link Cyberscore};
 * the status is workflow only and never enters the score. The name is unique
 * within the organization, which a unique constraint enforces. A deleted check
 * has no history worth keeping, so rows are deleted outright: the score
 * history in {@link CyberscoreSnapshot} keeps no link to a check.
 */
@Entity
@Table(name = "cyberscore_checks", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CyberscoreCheck extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Organization that owns the check, set once on create from the request's
     * checked organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", length = 5000, nullable = true)
    private String description;

    // What a passing answer looks like, so whoever performs the check can judge it.
    @Column(name = "expected", length = 5000, nullable = false)
    private String expected;

    // Share of the check in the score, from 1 to 100; the database checks the range.
    @Column(name = "weight", nullable = false)
    private Integer weight;

    // Position within the category, from 0 to 9999; the database checks the range.
    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    // -------------Enums-----------//

    // Null for an uncategorized check, which counts only towards the overall score.
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "category", nullable = true)
    private CyberscoreCheckCategory category;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "outcome", nullable = false)
    private CyberscoreCheckOutcome outcome = CyberscoreCheckOutcome.TODO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private CyberscoreCheckStatus status = CyberscoreCheckStatus.NOT_STARTED;
}
