package com.korur.oba.apps.kaya.seed.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.seed.dto.embedded.SeedReport;
import com.korur.oba.apps.kaya.seed.enums.SeedRunStatus;

/**
 * One import of today's repository files into the organization's packs and projects, with the
 * report it produced. A run is kept whatever its outcome, also when a refused composition discarded
 * every write of the run, so the OWNER can see why a seed did not pass. The organization and the
 * OWNER are plain ids, so this domain loads no identity entities; the database keys still hold.
 *
 * Written through {@link com.korur.oba.apps.kaya.seed.repository.SeedRunRepository} by
 * {@link com.korur.oba.apps.kaya.seed.service.SeedService} and
 * {@link com.korur.oba.apps.kaya.seed.helper.SeedRunRecorder}; no other domain reads it.
 */
@Entity
@Table(name = "seed_runs", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SeedRun extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // The git commit of the agents clone that was imported.
    @Column(name = "source_ref", length = 100, nullable = false, updatable = false)
    private String sourceRef;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, updatable = false)
    private SeedRunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report", columnDefinition = "jsonb", nullable = false, updatable = false)
    private SeedReport report;
}
