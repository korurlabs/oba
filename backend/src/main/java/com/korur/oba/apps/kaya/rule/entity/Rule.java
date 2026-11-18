package com.korur.oba.apps.kaya.rule.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A rule file of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered under .agents/rules/.
 *
 * The whole file, frontmatter included, is the blob named by contentBlobId and is delivered byte-exact;
 * the other columns are an index extracted from its frontmatter and never rendered back. An empty paths
 * list makes the rule always-on, so Claude Code loads it at every session start; the database checks
 * that alwaysOn and an empty paths list agree. Its convention links are the {@link RuleConvention}
 * rows, rewritten from the body on every parse so they cannot drift from the Markdown.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.rule.repository.RuleRepository} by
 * {@link com.korur.oba.apps.kaya.rule.service.RuleQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.rule.service.RuleService}, which the render domain calls on parse; composed
 * by the release domain.
 */
@Entity
@Table(name = "rules", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Rule extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    // Relative to .agents/rules/.
    @Column(name = "relative_path", length = 500, nullable = false)
    private String relativePath;

    @Column(name = "description", length = 4000)
    private String description;

    // The globs of the files the rule applies to; empty for an always-on rule.
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "paths", columnDefinition = "jsonb", nullable = false)
    private List<String> paths = new ArrayList<>();

    @Column(name = "always_on", nullable = false)
    private Boolean alwaysOn;

    // The path of an inherited rule this rule narrows, when a mounted pack overrides it.
    @Column(name = "overrides_path", length = 500)
    private String overridesPath;

    @Builder.Default
    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RuleConvention> conventions = new ArrayList<>();
}
