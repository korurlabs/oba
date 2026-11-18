package com.korur.oba.apps.kaya.release.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.release.dto.embedded.PackVersionEntry;
import com.korur.oba.apps.kaya.release.dto.embedded.ReleaseEntry;

/**
 * The immutable, signed composition of a repository's files that the CLI writes. Every column is
 * fixed at insert, because the signature covers the manifest, the number and the creation time; a
 * later change would make the stored signature lie. Numbers only grow per repository, a restore
 * included, so the CLI can refuse a release lower than the one it holds.
 *
 * The manifest is a list of {@link ReleaseEntry}s, each naming the pack version its path comes
 * from, so the source versions of a release are known without a join. The pack versions list every
 * composed pack, ancestors and packs that deliver no path included, outside the signed document.
 *
 * Read through {@link com.korur.oba.apps.kaya.release.repository.ReleaseRepository} by
 * {@link com.korur.oba.apps.kaya.release.service.ReleaseQueryService}, which session and delivery use;
 * written only by {@link com.korur.oba.apps.kaya.release.service.ReleaseService}.
 */
@Entity
@Table(
        name = "releases",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "releases_repository_id_number_uk",
                columnNames = {"repository_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Release extends BaseEntity {

    @Column(name = "repository_id", nullable = false, updatable = false)
    private UUID repositoryId;

    @Column(name = "number", nullable = false, updatable = false)
    private Integer number;

    // The approved version, or for a pack order change the version the composition rests on,
    // that led to this release.
    @Column(name = "cause_version_id", nullable = false, updatable = false)
    private UUID causeVersionId;

    // Ordered by path code point by code point, the order of the signed document.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest", columnDefinition = "jsonb", nullable = false, updatable = false)
    private List<ReleaseEntry> manifest;

    // Every composed pack with its version and mount folder, in composition order; not signed.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pack_versions", columnDefinition = "jsonb", nullable = false, updatable = false)
    private List<PackVersionEntry> packVersions;

    // SHA-256 of the canonical signed document, which the CLI compares before downloading.
    @Column(name = "manifest_sha256", length = 64, nullable = false, updatable = false)
    private String manifestSha256;

    // Base64 Ed25519 signature of the canonical signed document.
    @Column(name = "signature", length = 88, nullable = false, updatable = false)
    private String signature;

    @Column(name = "key_id", length = 40, nullable = false, updatable = false)
    private String keyId;
}
