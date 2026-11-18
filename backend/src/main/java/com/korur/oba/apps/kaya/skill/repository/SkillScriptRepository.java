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
import com.korur.oba.apps.kaya.skill.entity.SkillScript;

/**
 * Reads the {@link SkillScript} links of the skill domain; they are written with their skill.
 */
@Repository
public interface SkillScriptRepository extends JpaRepository<SkillScript, UUID>, JpaSpecificationExecutor<SkillScript> {

    List<SkillScript> findBySkill_IdOrderByPathAsc(UUID skillId);

    /**
     * Every link to one script, with its skill, so a page can name the skills that share it.
     *
     * @param scriptId the script
     * @param organizationId the organization of the skills
     * @return the links, one per skill
     */
    @EntityGraph(attributePaths = {"skill"})
    List<SkillScript> findByScriptIdAndSkill_OrganizationId(UUID scriptId, UUID organizationId);

    /**
     * How many skills link each of the given scripts, so a page can warn that a change to a shared
     * script reaches every skill that uses it.
     *
     * @param partIds the script ids, not empty
     * @return one count per linked script; a script no skill links is left out
     */
    @Query("SELECT l.scriptId AS partId, COUNT(l) AS skills FROM SkillScript l WHERE l.scriptId IN :partIds GROUP BY l.scriptId")
    List<SkillPartUsageProjection> countSkillsByScriptIds(@Param("partIds") Collection<UUID> partIds);
}
