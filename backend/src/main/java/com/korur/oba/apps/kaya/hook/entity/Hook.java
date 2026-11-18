package com.korur.oba.apps.kaya.hook.entity;

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
 * A hook script of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered under
 * .agents/hooks/ and run by the hook commands of the settings file.
 *
 * The whole file is the blob named by contentBlobId and is delivered byte-exact; the executable flag is
 * taken from the file mode on parse and gives the delivered file mode EXECUTABLE on render. A path is
 * unique within its version.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.hook.repository.HookRepository} by
 * {@link com.korur.oba.apps.kaya.hook.service.HookQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.hook.service.HookService}, which the render domain calls on
 * parse; composed by the release domain.
 */
@Entity
@Table(name = "hooks", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Hook extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    // Relative to .agents/hooks/.
    @Column(name = "relative_path", length = 500, nullable = false)
    private String relativePath;

    // Delivered with mode EXECUTABLE when true, so the settings file can run the script directly.
    @Builder.Default
    @Column(name = "executable", nullable = false)
    private Boolean executable = false;
}
