package com.korur.oba.apps.kaya.toolparameter.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.toolparameter.enums.ToolParameterFormat;

/**
 * A file of parameters of a skill's own tools in one {@link com.korur.oba.apps.kaya.version.entity.Version},
 * such as agents/openai.yml or evals/evals.json, delivered only inside the skills that link it, at the path
 * each link names.
 *
 * The whole file is the blob named by contentBlobId and is delivered byte-exact; the name and the
 * {@link ToolParameterFormat} are an index extracted on parse and never rendered back. A tool parameter
 * file has no path of its own, so two skills of one version that hold the same file share one row.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version domain
 * and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.toolparameter.repository.ToolParameterRepository} by
 * {@link com.korur.oba.apps.kaya.toolparameter.service.ToolParameterQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.toolparameter.service.ToolParameterService}, which the render domain calls
 * on parse; linked by the skill domain.
 */
@Entity
@Table(name = "tool_parameters", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ToolParameter extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    @Column(name = "name", length = 300, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "format", nullable = false)
    private ToolParameterFormat format;
}
