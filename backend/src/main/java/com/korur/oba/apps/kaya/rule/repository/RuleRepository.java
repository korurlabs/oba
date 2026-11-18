package com.korur.oba.apps.kaya.rule.repository;

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
import com.korur.oba.apps.kaya.rule.entity.Rule;

/**
 * Reads and writes the {@link Rule} rows of the rule domain.
 */
@Repository
public interface RuleRepository extends JpaRepository<Rule, UUID>, JpaSpecificationExecutor<Rule> {

    Optional<Rule> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Rule> findByOrganizationIdAndVersionIdOrderByRelativePathAsc(UUID organizationId, UUID versionId);

    /**
     * The rules of some versions that cite one convention path.
     *
     * @param organizationId the organization of the versions
     * @param versionIds     the versions, not empty
     * @param conventionPath the convention path relative to the repository root
     * @return the rules, by version and path
     */
    @Query("SELECT DISTINCT r FROM Rule r JOIN r.conventions c WHERE r.organizationId = :organizationId"
            + " AND r.versionId IN :versionIds AND c.conventionPath = :conventionPath"
            + " ORDER BY r.versionId, r.relativePath")
    List<Rule> findCitingConvention(@Param("organizationId") UUID organizationId,
            @Param("versionIds") Collection<UUID> versionIds, @Param("conventionPath") String conventionPath);

    /**
     * Deletes every rule of a version; the database removes their convention links with them.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of rules deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Rule r WHERE r.organizationId = :organizationId AND r.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes the rule at one path of a version; the database removes its convention links with it.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path
     * @return the number of rules deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Rule r WHERE r.organizationId = :organizationId AND r.versionId = :versionId"
            + " AND r.relativePath = :relativePath")
    int deleteByPath(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("relativePath") String relativePath);
}
