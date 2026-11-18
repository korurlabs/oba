package com.korur.oba.apps.kaya.blob.entity;

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
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * File content stored once per organization and addressed by its SHA-256, so versions that keep a
 * file unchanged share one row and the CLI downloads it once.
 *
 * A blob is never changed after it is written; its hash is its identity, and the database checks
 * that the hash is 64 lower-case hex characters and that size equals the content's byte length.
 * Loading a blob loads its content (up to 5 MiB), so lists of files read the hash and size
 * without it.
 *
 * Read through {@link com.korur.oba.apps.kaya.blob.repository.BlobRepository} by
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}, which the version, release,
 * delivery and seed domains call; written only through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobService}.
 */
@Entity
@Table(
        name = "blobs",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "blobs_organization_id_sha256_uk",
                columnNames = {"organization_id", "sha256"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Blob extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // 64 lower-case hex characters.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", length = 64, nullable = false, updatable = false)
    private String sha256;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    // Byte length of the content; the database checks that both agree.
    @Column(name = "size", nullable = false, updatable = false)
    private Integer size;
}
