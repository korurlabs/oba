package com.korur.oba.apps.kaya.script.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * An executable helper or other script of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered
 * only inside the skills that link it, at the path each link names.
 *
 * The whole file is the blob named by contentBlobId and is delivered byte-exact; the name and the executable
 * flag are an index extracted on parse and never rendered back. An executable script is delivered with the
 * executable mode, so a change to one counts as an executable change. A script has no path of its own, so
 * two skills of one version that hold the same script share one row.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version domain
 * and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.script.repository.ScriptRepository} by
 * {@link com.korur.oba.apps.kaya.script.service.ScriptQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.script.service.ScriptService}, which the render domain calls on parse;
 * linked by the skill domain.
 */
@Entity
@Table(name = "scripts", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Script extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    @Column(name = "name", length = 300, nullable = false)
    private String name;

    @Builder.Default
    @Column(name = "executable", nullable = false)
    private Boolean executable = false;
}
