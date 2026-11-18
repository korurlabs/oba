package com.korur.oba.apps.kaya.skill.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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
import com.korur.oba.apps.kaya.skill.dto.projection.SkillPartCountProjection;
import com.korur.oba.apps.kaya.skill.entity.Skill;

/**
 * Reads and writes the {@link Skill} rows of the skill domain.
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID>, JpaSpecificationExecutor<Skill> {

    Optional<Skill> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Skill> findByOrganizationIdAndVersionIdOrderByNameAsc(UUID organizationId, UUID versionId);

    long countByOrganizationIdAndVersionIdIn(UUID organizationId, Collection<UUID> versionIds);

    /**
     * Counts the part links of the skills of the given versions per part kind, across the five link
     * tables in one query, so a count of delivered files loads no link.
     *
     * @param organizationId the organization of the skills
     * @param versionIds     the versions, not empty
     * @return one row per part kind, its count 0 when no skill links a part of that kind
     */
    @Query(value = """
            SELECT 'WORKFLOW' AS kind, COUNT(l.id) AS links
            FROM kaya.skill_workflows l
            JOIN kaya.skills s ON s.id = l.skill_id
            WHERE s.organization_id = :organizationId AND s.version_id IN (:versionIds)
            UNION ALL
            SELECT 'SCRIPT' AS kind, COUNT(l.id) AS links
            FROM kaya.skill_scripts l
            JOIN kaya.skills s ON s.id = l.skill_id
            WHERE s.organization_id = :organizationId AND s.version_id IN (:versionIds)
            UNION ALL
            SELECT 'CONVENTION' AS kind, COUNT(l.id) AS links
            FROM kaya.skill_conventions l
            JOIN kaya.skills s ON s.id = l.skill_id
            WHERE s.organization_id = :organizationId AND s.version_id IN (:versionIds)
            UNION ALL
            SELECT 'REFERENCE' AS kind, COUNT(l.id) AS links
            FROM kaya.skill_references l
            JOIN kaya.skills s ON s.id = l.skill_id
            WHERE s.organization_id = :organizationId AND s.version_id IN (:versionIds)
            UNION ALL
            SELECT 'TOOL_PARAMETER' AS kind, COUNT(l.id) AS links
            FROM kaya.skill_tool_parameters l
            JOIN kaya.skills s ON s.id = l.skill_id
            WHERE s.organization_id = :organizationId AND s.version_id IN (:versionIds)
            """, nativeQuery = true)
    List<SkillPartCountProjection> countPartLinksByVersionIds(@Param("organizationId") UUID organizationId,
            @Param("versionIds") Collection<UUID> versionIds);

    Optional<Skill> findByOrganizationIdAndVersionIdAndName(UUID organizationId, UUID versionId, String name);

    /**
     * Deletes every skill of a version; the database removes their part links with them.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of skills deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Skill s WHERE s.organizationId = :organizationId AND s.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes one skill of a version by name; the database removes its part links with it.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param name           the skill name
     * @return the number of skills deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Skill s WHERE s.organizationId = :organizationId AND s.versionId = :versionId"
            + " AND s.name = :name")
    int deleteByName(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("name") String name);
}
