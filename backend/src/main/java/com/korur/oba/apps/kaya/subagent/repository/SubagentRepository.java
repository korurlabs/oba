package com.korur.oba.apps.kaya.subagent.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.subagent.entity.Subagent;

/**
 * Reads and writes the {@link Subagent} rows of the subagent domain.
 */
@Repository
public interface SubagentRepository extends JpaRepository<Subagent, UUID>, JpaSpecificationExecutor<Subagent> {

    Optional<Subagent> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Subagent> findByOrganizationIdAndVersionIdOrderByRelativePathAsc(UUID organizationId, UUID versionId);

    /**
     * Deletes every subagent of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of subagents deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Subagent c WHERE c.organizationId = :organizationId AND c.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes the subagent at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path
     * @return the number of subagents deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Subagent c WHERE c.organizationId = :organizationId AND c.versionId = :versionId"
            + " AND c.relativePath = :relativePath")
    int deleteByPath(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("relativePath") String relativePath);
}
