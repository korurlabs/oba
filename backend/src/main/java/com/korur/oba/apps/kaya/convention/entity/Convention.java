package com.korur.oba.apps.kaya.convention.entity;

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
 * A convention document of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered under
 * docs/private/conventions/, or only inside the skills that link it when it has no path.
 *
 * The whole file, frontmatter included, is the blob named by contentBlobId and is delivered byte-exact;
 * the title and the overridden path are an index extracted from it and never rendered back. A path is
 * unique within its version.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.convention.repository.ConventionRepository} by
 * {@link com.korur.oba.apps.kaya.convention.service.ConventionQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.convention.service.ConventionService}, which the render domain calls on
 * parse; linked by the skill domain and composed by the release domain.
 */
@Entity
@Table(name = "conventions", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Convention extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    // Relative to docs/private/conventions/; null for a convention delivered only inside skills.
    @Column(name = "relative_path", length = 500)
    private String relativePath;

    @Column(name = "title", length = 300)
    private String title;

    // The path of an inherited convention this convention replaces, when a mounted pack overrides it.
    @Column(name = "overrides_path", length = 500)
    private String overridesPath;
}
