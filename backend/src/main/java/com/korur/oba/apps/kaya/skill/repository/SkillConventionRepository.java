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
import com.korur.oba.apps.kaya.skill.entity.SkillConvention;

/**
 * Reads the {@link SkillConvention} links of the skill domain; they are written with their skill.
 */
@Repository
public interface SkillConventionRepository extends JpaRepository<SkillConvention, UUID>, JpaSpecificationExecutor<SkillConvention> {

    List<SkillConvention> findBySkill_IdOrderByPathAsc(UUID skillId);

    /**
     * Every link to one convention, with its skill, so a page can name the skills that share it.
     *
     * @param conventionId the convention
     * @param organizationId the organization of the skills
     * @return the links, one per skill
     */
    @EntityGraph(attributePaths = {"skill"})
    List<SkillConvention> findByConventionIdAndSkill_OrganizationId(UUID conventionId, UUID organizationId);

    /**
     * How many skills link each of the given conventions, so a page can warn that a change to a shared
     * convention reaches every skill that uses it.
     *
     * @param partIds the convention ids, not empty
     * @return one count per linked convention; a convention no skill links is left out
     */
    @Query("SELECT l.conventionId AS partId, COUNT(l) AS skills FROM SkillConvention l WHERE l.conventionId IN :partIds GROUP BY l.conventionId")
    List<SkillPartUsageProjection> countSkillsByConventionIds(@Param("partIds") Collection<UUID> partIds);
}
