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
import com.korur.oba.apps.kaya.skill.entity.SkillToolParameter;

/**
 * Reads the {@link SkillToolParameter} links of the skill domain; they are written with their skill.
 */
@Repository
public interface SkillToolParameterRepository extends JpaRepository<SkillToolParameter, UUID>, JpaSpecificationExecutor<SkillToolParameter> {

    List<SkillToolParameter> findBySkill_IdOrderByPathAsc(UUID skillId);

    /**
     * Every link to one tool parameter, with its skill, so a page can name the skills that share it.
     *
     * @param toolParameterId the tool parameter
     * @param organizationId the organization of the skills
     * @return the links, one per skill
     */
    @EntityGraph(attributePaths = {"skill"})
    List<SkillToolParameter> findByToolParameterIdAndSkill_OrganizationId(UUID toolParameterId, UUID organizationId);

    /**
     * How many skills link each of the given tool parameters, so a page can warn that a change to a shared
     * tool parameter reaches every skill that uses it.
     *
     * @param partIds the tool parameter ids, not empty
     * @return one count per linked tool parameter; a tool parameter no skill links is left out
     */
    @Query("SELECT l.toolParameterId AS partId, COUNT(l) AS skills FROM SkillToolParameter l WHERE l.toolParameterId IN :partIds GROUP BY l.toolParameterId")
    List<SkillPartUsageProjection> countSkillsByToolParameterIds(@Param("partIds") Collection<UUID> partIds);
}
