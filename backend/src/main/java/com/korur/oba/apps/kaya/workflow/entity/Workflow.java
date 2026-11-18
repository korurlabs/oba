package com.korur.oba.apps.kaya.workflow.entity;

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
 * A workflow file of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered under
 * .agents/workflows/, or only inside the skills that link it when it has no path.
 *
 * The whole file is the blob named by contentBlobId and is delivered byte-exact; the title is an index
 * extracted from its frontmatter and never rendered back. A path is unique within its version.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.workflow.repository.WorkflowRepository} by
 * {@link com.korur.oba.apps.kaya.workflow.service.WorkflowQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.workflow.service.WorkflowService}, which the render domain calls on
 * parse; linked by the skill domain and composed by the release domain.
 */
@Entity
@Table(name = "workflows", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Workflow extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    // Relative to .agents/workflows/; null for a workflow delivered only inside skills.
    @Column(name = "relative_path", length = 500)
    private String relativePath;

    @Column(name = "title", length = 300)
    private String title;
}
