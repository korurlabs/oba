package com.korur.oba.apps.kaya.skill.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.skill.dto.projection.SkillPartUsageProjection;
import com.korur.oba.apps.kaya.skill.entity.SkillWorkflow;

/**
 * Reads the {@link SkillWorkflow} links of the skill domain; they are written with their skill.
 */
@Repository
public interface SkillWorkflowRepository extends JpaRepository<SkillWorkflow, UUID>, JpaSpecificationExecutor<SkillWorkflow> {

    List<SkillWorkflow> findBySkill_IdOrderByPathAsc(UUID skillId);

    /**
     * Every link to one workflow, with its skill, so a page can name the skills that share it.
     *
     * @param workflowId the workflow
     * @param organizationId the organization of the skills
     * @return the links, one per skill
     */
    @EntityGraph(attributePaths = {"skill"})
    List<SkillWorkflow> findByWorkflowIdAndSkill_OrganizationId(UUID workflowId, UUID organizationId);

    /**
     * How many skills link each of the given workflows, so a page can warn that a change to a shared
     * workflow reaches every skill that uses it.
     *
     * @param partIds the workflow ids, not empty
     * @return one count per linked workflow; a workflow no skill links is left out
     */
    @Query("SELECT l.workflowId AS partId, COUNT(l) AS skills FROM SkillWorkflow l WHERE l.workflowId IN :partIds GROUP BY l.workflowId")
    List<SkillPartUsageProjection> countSkillsByWorkflowIds(@Param("partIds") Collection<UUID> partIds);
}
