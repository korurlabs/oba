package com.korur.oba.apps.kaya.reference.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A documentation or asset file of one {@link com.korur.oba.apps.kaya.version.entity.Version}, such as a file
 * under references/ or assets/ of a skill, delivered only inside the skills that link it, at the path each
 * link names.
 *
 * The whole file is the blob named by contentBlobId and is delivered byte-exact; it may be binary, such as
 * an image. The name and the media type are an index extracted on parse and never rendered back. A
 * reference has no path of its own, so two skills of one version that hold the same file share one row.
 * The table is reference_files because references is reserved in SQL.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version domain
 * and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.reference.repository.ReferenceRepository} by
 * {@link com.korur.oba.apps.kaya.reference.service.ReferenceQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.reference.service.ReferenceService}, which the render domain calls on parse;
 * linked by the skill domain.
 */
@Entity
@Table(name = "reference_files", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Reference extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    @Column(name = "name", length = 300, nullable = false)
    private String name;

    @Column(name = "media_type", length = 100, nullable = false)
    private String mediaType;
}
