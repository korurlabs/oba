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

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * One {@link com.korur.oba.apps.kaya.pack.entity.Pack} a repository composes, at the repository root
 * or mounted in a folder. The position is the display order; {@link com.korur.oba.apps.kaya.release.helper.Composer}
 * orders the packs by the pack tree. A repository mounts at most one pack in a folder, and a folder
 * lies inside or around no other mount's folder.
 *
 * Both ids are plain columns: the pack and the repository belong to other domains, and the
 * application checks that the pack is of the repository's organization. Rows carry no
 * organization id; they are reached through their repository.
 *
 * Read and written through {@link com.korur.oba.apps.kaya.release.repository.RepositoryPackRepository}
 * by {@link com.korur.oba.apps.kaya.release.service.RepositoryPackService} and
 * {@link com.korur.oba.apps.kaya.release.service.RepositoryPackQueryService}; composed by
 * {@link com.korur.oba.apps.kaya.release.helper.Composer}.
 */
@Entity
@Table(
        name = "repository_packs",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "repository_packs_repository_id_pack_id_uk",
                columnNames = {"repository_id", "pack_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RepositoryPack extends BaseEntity {

    @Column(name = "repository_id", nullable = false, updatable = false)
    private UUID repositoryId;

    @Column(name = "pack_id", nullable = false, updatable = false)
    private UUID packId;

    @Column(name = "position", nullable = false)
    private Integer position;

    // The mount folder, relative to the repository root; null for a pack composed at the root.
    @Column(name = "root_path", length = 200)
    private String rootPath;
}
