package com.korur.oba.apps.kazan.cyberscore.entity;

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
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreSnapshotEventType;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity recording a past value of an organization's {@link Cyberscore}, the
 * source of the trend chart.
 *
 * Append only: a row is inserted when a check change moves the percentage or
 * the level, and is never updated or deleted by the application. It
 * keeps no link to a check, so the history survives deleting one. Every column
 * is therefore fixed after insert.
 */
@Entity
@Table(name = "cyberscore_snapshots", schema = "kazan")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CyberscoreSnapshot extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "score_percentage", nullable = false, updatable = false)
    private Integer scorePercentage;

    /**
     * User whose change caused the snapshot. Null for a system change, and
     * cleared by the database when the user is deleted, so the history
     * outlives its author.
     */
    @Column(name = "performed_by_id", nullable = true, updatable = false)
    private UUID performedById;

    // Moment the score changed, from the injected clock.
    @Column(name = "taken_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false, updatable = false)
    private Instant takenAt;

    // -------------Enums-----------//

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "score_level", nullable = false, updatable = false)
    private CyberscoreLevel scoreLevel;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "event_type", nullable = false, updatable = false)
    private CyberscoreSnapshotEventType eventType;
}
