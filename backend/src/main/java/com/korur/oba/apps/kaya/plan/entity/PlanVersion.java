package com.korur.oba.apps.kaya.plan.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.plan.enums.PlanVersionSource;

/**
 * One captured content of a {@link Plan}, numbered 1, 2, ... per plan. A version is never changed:
 * feedback is anchored to the lines of the version it was given on, so the content must stay as it
 * was captured. The plan and the writing session are plain ids; the session id is cleared when that
 * session is deleted, and the version stays.
 */
@Entity
@Table(
        name = "plan_versions",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "plan_versions_plan_id_number_uk", columnNames = {"plan_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PlanVersion extends BaseEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "number", nullable = false, updatable = false)
    private int number;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "source", nullable = false, updatable = false)
    private PlanVersionSource source;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String content;

    // Lower-case hex SHA-256 of the content; an unchanged capture is recognised by it.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", length = 64, nullable = false, updatable = false)
    private String contentSha256;

    @Column(name = "line_count", nullable = false, updatable = false)
    private int lineCount;

    @Column(name = "captured_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false, updatable = false)
    private Instant capturedAt;
}
