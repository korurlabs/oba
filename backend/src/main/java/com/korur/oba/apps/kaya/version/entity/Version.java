package com.korur.oba.apps.kaya.version.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.version.enums.VersionSource;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;

/**
 * One numbered version of a pack: the complete content of its pack at that point, held in the file
 * kind tables that reference this row. The pack and the proposer are plain ids, so this domain never
 * loads pack or identity entities.
 */
@Entity
@Table(name = "versions", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Version extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "pack_id", nullable = false, updatable = false)
    private UUID packId;

    @Column(name = "number", nullable = false, updatable = false)
    private Integer number;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private VersionStatus status = VersionStatus.DRAFT;

    // The version this one was derived from; null for an owner's first version.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_version_id")
    private Version baseVersion;

    @Column(name = "proposed_by", nullable = false, updatable = false)
    private UUID proposedBy;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "source", nullable = false, updatable = false)
    private VersionSource source;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Builder.Default
    @Column(name = "executable_change", nullable = false)
    private Boolean executableChange = false;

    @Column(name = "manifest_sha256", length = 64)
    private String manifestSha256;

    @Column(name = "submitted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant submittedAt;

    @Column(name = "decided_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant decidedAt;

}
