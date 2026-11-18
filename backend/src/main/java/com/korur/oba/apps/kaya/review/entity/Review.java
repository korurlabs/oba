package com.korur.oba.apps.kaya.review.entity;

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
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.review.enums.ReviewDecision;

/**
 * One decision or comment of a reviewer on a version in review. A review is never changed: a
 * version that was rejected, changed and submitted again collects its new reviews beside the old
 * ones, so the history of the change stays readable. The version and the reviewer are plain ids;
 * the reviews are read by version, never through the version entity.
 *
 * Stored through {@link com.korur.oba.apps.kaya.review.repository.ReviewRepository} by
 * {@link com.korur.oba.apps.kaya.review.service.ReviewService} and listed with their change by
 * {@link com.korur.oba.apps.kaya.review.service.ReviewQueryService}.
 */
@Entity
@Table(name = "reviews", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Review extends BaseEntity {

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "decision", nullable = false, updatable = false)
    private ReviewDecision decision;

    @Column(name = "comment", length = 2000, updatable = false)
    private String comment;
}
