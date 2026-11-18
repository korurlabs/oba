package com.korur.oba.apps.kaya.skill.entity;

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

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A skill of one {@link com.korur.oba.apps.kaya.version.entity.Version}, delivered as
 * .agents/skills/name/SKILL.md with the symlink .claude/skills/name pointing at that directory.
 *
 * The blob named by contentBlobId is the whole SKILL.md, frontmatter included, delivered byte-exact; name
 * and description are an index extracted from its frontmatter and never rendered back. The name is
 * unique within the version. The other files of the skill directory are parts of their own kind, linked
 * through the {@link SkillWorkflow}, {@link SkillScript}, {@link SkillConvention}, {@link SkillReference}
 * and {@link SkillToolParameter} rows, which keep each file's path inside the directory so the links
 * inside SKILL.md still resolve. A skill links only parts of its own version; two skills of one version
 * may share a part.
 *
 * The organization, the version and the blob are plain ids, so this domain sits below the version
 * domain and loads no entity of another domain.
 *
 * Read through {@link com.korur.oba.apps.kaya.skill.repository.SkillRepository} by
 * {@link com.korur.oba.apps.kaya.skill.service.SkillQueryService}; written only through
 * {@link com.korur.oba.apps.kaya.skill.service.SkillService}, which the render domain calls on parse; composed
 * by the release domain.
 */
@Entity
@Table(name = "skills", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Skill extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "content_blob_id", nullable = false, updatable = false)
    private UUID contentBlobId;

    // The directory name below .agents/skills/, from the frontmatter.
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Builder.Default
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillWorkflow> workflows = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillScript> scripts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillConvention> conventions = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillReference> references = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillToolParameter> toolParameters = new ArrayList<>();
}
