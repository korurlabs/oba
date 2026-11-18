package com.korur.oba.apps.kaya.subagent.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A subagent definition of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered under
 * .claude/agents/.
 *
 * The whole file, frontmatter included, is the blob named by contentBlobId and is delivered byte-exact;
 * the name, description, model, turn limit and tools are an index extracted from its frontmatter and
 * never rendered back. Both the path and the name are unique within a version, because the agent tool
 * selects a subagent by its name. A turn limit, when set, is at least 1.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.subagent.repository.SubagentRepository} by
 * {@link com.korur.oba.apps.kaya.subagent.service.SubagentQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.subagent.service.SubagentService}, which the render domain calls on
 * parse; composed by the release domain.
 */
@Entity
@Table(name = "subagents", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Subagent extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    // Relative to .claude/agents/.
    @Column(name = "relative_path", length = 500, nullable = false)
    private String relativePath;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "max_turns")
    private Integer maxTurns;

    // Null when the frontmatter names no tools, so the subagent inherits the tools of its session.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tools", columnDefinition = "jsonb")
    private List<String> tools;
}
