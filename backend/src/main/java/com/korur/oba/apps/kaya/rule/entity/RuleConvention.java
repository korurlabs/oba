package com.korur.oba.apps.kaya.rule.entity;

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

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A link from a {@link Rule}'s body to a convention under docs/private/conventions/, one per cited path.
 *
 * The target is stored as a path, not as a convention id, because a project rule may cite a convention
 * of a pack, which lives in another version. The rows are replaced from the rule's body on every parse
 * and go with their rule.
 *
 * Read through {@link com.korur.oba.apps.kaya.rule.repository.RuleConventionRepository} by
 * {@link com.korur.oba.apps.kaya.rule.service.RuleQueryService}; written with its rule by
 * {@link com.korur.oba.apps.kaya.rule.service.RuleService}.
 */
@Entity
@Table(name = "rule_conventions", schema = "kaya")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RuleConvention extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false, updatable = false)
    private Rule rule;

    // Relative to the repository root, as docs/private/conventions/backend/logging.md.
    @Column(name = "convention_path", length = 500, nullable = false, updatable = false)
    private String conventionPath;
}
