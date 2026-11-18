package com.korur.oba.apps.kaizen.milestone.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.milestone.dto.projection.MilestoneDependencyProjection;
import com.korur.oba.apps.kaizen.milestone.entity.MilestoneDependency;

/**
 * Repository interface for milestone dependency rows. Rows are hard-deleted.
 */
@Repository
public interface MilestoneDependencyRepository extends JpaRepository<MilestoneDependency, UUID> {

    /**
     * Finds the row stating that a milestone depends on another.
     *
     * @param milestoneId          ID of the dependent milestone
     * @param dependsOnMilestoneId ID of the milestone it depends on
     * @return the row if the pair exists
     */
    Optional<MilestoneDependency> findByMilestoneIdAndDependsOnMilestoneId(UUID milestoneId,
            UUID dependsOnMilestoneId);

    /**
     * Tells whether a milestone already depends on another.
     *
     * @param milestoneId          ID of the dependent milestone
     * @param dependsOnMilestoneId ID of the milestone it depends on
     * @return true when the pair exists
     */
    boolean existsByMilestoneIdAndDependsOnMilestoneId(UUID milestoneId, UUID dependsOnMilestoneId);

    /**
     * Finds the milestones that the given milestones depend on, one step deep.
     *
     * Rows of soft-deleted milestones are included on purpose: the cycle check
     * walks them so that restoring a milestone can never complete a cycle.
     *
     * @param milestoneIds IDs of the dependent milestones, not empty
     * @return IDs of the milestones they depend on, without duplicates
     */
    @Query(value = """
            SELECT DISTINCT d.depends_on_milestone_id
            FROM kaizen.milestone_dependencies d
            WHERE d.milestone_id IN (:milestoneIds)
            """, nativeQuery = true)
    List<UUID> findDependsOnIdsByMilestoneIds(@Param("milestoneIds") Collection<UUID> milestoneIds);

    /**
     * Finds the dependencies of the given milestones whose other end is a live milestone.
     *
     * @param milestoneIds IDs of the dependent milestones, not empty
     * @return one row per dependency on a live milestone
     */
    @Query(value = """
            SELECT d.milestone_id AS milestoneId,
                   d.depends_on_milestone_id AS dependsOnMilestoneId
            FROM kaizen.milestone_dependencies d
            JOIN kaizen.milestones m ON m.id = d.depends_on_milestone_id
            WHERE d.milestone_id IN (:milestoneIds)
              AND m.deleted = false
            """, nativeQuery = true)
    List<MilestoneDependencyProjection> findLiveDependencies(@Param("milestoneIds") Collection<UUID> milestoneIds);
}
