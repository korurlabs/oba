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
import com.korur.oba.apps.kaya.skill.entity.SkillReference;

/**
 * Reads the {@link SkillReference} links of the skill domain; they are written with their skill.
 */
@Repository
public interface SkillReferenceRepository extends JpaRepository<SkillReference, UUID>, JpaSpecificationExecutor<SkillReference> {

    List<SkillReference> findBySkill_IdOrderByPathAsc(UUID skillId);

    /**
     * Every link to one reference file, with its skill, so a page can name the skills that share it.
     *
     * @param referenceId the reference file
     * @param organizationId the organization of the skills
     * @return the links, one per skill
     */
    @EntityGraph(attributePaths = {"skill"})
    List<SkillReference> findByReferenceIdAndSkill_OrganizationId(UUID referenceId, UUID organizationId);

    /**
     * How many skills link each of the given reference files, so a page can warn that a change to a shared
     * reference file reaches every skill that uses it.
     *
     * @param partIds the reference file ids, not empty
     * @return one count per linked reference file; a reference file no skill links is left out
     */
    @Query("SELECT l.referenceId AS partId, COUNT(l) AS skills FROM SkillReference l WHERE l.referenceId IN :partIds GROUP BY l.referenceId")
    List<SkillPartUsageProjection> countSkillsByReferenceIds(@Param("partIds") Collection<UUID> partIds);
}
