package com.korur.oba.apps.kaya.role.entity;

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
 * A role description of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered under
 * .agents/roles/.
 *
 * The whole file, frontmatter included, is the blob named by contentBlobId and is delivered byte-exact;
 * the title is an index extracted from it and never rendered back. A path is unique within its version.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.role.repository.RoleRepository} by
 * {@link com.korur.oba.apps.kaya.role.service.RoleQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.role.service.RoleService}, which the render domain calls on parse;
 * composed by the release domain.
 */
@Entity
@Table(name = "roles", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Role extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    // Relative to .agents/roles/.
    @Column(name = "relative_path", length = 500, nullable = false)
    private String relativePath;

    @Column(name = "title", length = 300)
    private String title;
}
