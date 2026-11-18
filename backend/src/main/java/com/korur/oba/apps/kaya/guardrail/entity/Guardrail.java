package com.korur.oba.apps.kaya.guardrail.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.guardrail.enums.GuardrailCategory;

/**
 * A guardrail of one {@link com.korur.oba.apps.kaya.version.entity.Version}: a standing limit for the
 * organization's agents, delivered as .agents/rules/guardrails/name.md in a pack or a repository's root
 * project.
 *
 * The whole file is the blob named by contentBlobId and is delivered byte-exact, so its body loads as an
 * always-on rule; the category, the description and the deny rules are an index extracted from its
 * frontmatter and never rendered back. The deny rules are Claude Code permission rules that the delivered
 * settings deny, at most 20, which the database also checks. The name is the file name without .md and is
 * unique within its version.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version domain
 * and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.guardrail.repository.GuardrailRepository} by
 * {@link com.korur.oba.apps.kaya.guardrail.service.GuardrailQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.guardrail.service.GuardrailService}, which the render domain calls on parse;
 * composed by the release domain.
 */
@Entity
@Table(name = "guardrails", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Guardrail extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    // The file name below .agents/rules/guardrails/ without .md.
    @Column(name = "name", length = 60, nullable = false)
    private String name;

    // Mapped to the PostgreSQL enum guardrail_guardrail_category_enum.
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "category", nullable = false)
    private GuardrailCategory category;

    @Column(name = "description", length = 4000)
    private String description;

    // The permission rules the delivered .claude/settings.json denies; empty for an instruction only.
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deny", columnDefinition = "jsonb", nullable = false)
    private List<String> deny = new ArrayList<>();
}
