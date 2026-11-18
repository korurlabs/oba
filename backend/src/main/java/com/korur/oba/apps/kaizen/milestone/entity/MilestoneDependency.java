package com.korur.oba.apps.kaizen.milestone.entity;

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
 * Entity stating that one milestone depends on another: the dependent should
 * not start before the dependency's target date.
 *
 * A row is a fact, not a record to restore, so it is hard-deleted. Both ends
 * are plain id columns rather than relations, so that loading a row never
 * trips the soft-delete filter of Milestone; the database removes the row
 * when either milestone row is removed.
 */
@Entity
@Table(name = "milestone_dependencies", schema = "kaizen")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MilestoneDependency extends BaseEntity {

    // -------------Atts-----------//

    /**
     * The dependent milestone, which should not start before the other one is due.
     */
    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;

    /**
     * The milestone that the dependent waits for.
     */
    @Column(name = "depends_on_milestone_id", nullable = false)
    private UUID dependsOnMilestoneId;
}
