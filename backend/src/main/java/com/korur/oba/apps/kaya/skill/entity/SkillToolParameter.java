package com.korur.oba.apps.kaya.skill.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * A link from a {@link Skill} to a {@link com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter} of the same version, at the path the file has inside
 * the skill directory.
 *
 * The tool parameter is a plain id, because it belongs to another domain; the database removes the link with its
 * skill and with its tool parameter. A skill links one tool parameter once and uses one path once.
 *
 * Read through {@link com.korur.oba.apps.kaya.skill.repository.SkillToolParameterRepository} by
 * {@link com.korur.oba.apps.kaya.skill.service.SkillQueryService}; written with its skill by
 * {@link com.korur.oba.apps.kaya.skill.service.SkillService}.
 */
@Entity
@Table(name = "skill_tool_parameters", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SkillToolParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false, updatable = false)
    private Skill skill;

    @Column(name = "tool_parameter_id", nullable = false, updatable = false)
    private UUID toolParameterId;

    // Relative to the skill directory, as the links inside SKILL.md name the file.
    @Column(name = "path", length = 300, nullable = false, updatable = false)
    private String path;
}
