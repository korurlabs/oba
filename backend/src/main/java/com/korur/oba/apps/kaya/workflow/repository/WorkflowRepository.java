package com.korur.oba.apps.kaya.workflow.repository;

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
import com.korur.oba.apps.kaya.workflow.entity.Workflow;

/**
 * Reads and writes the {@link Workflow} rows of the workflow domain.
 */
@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID>, JpaSpecificationExecutor<Workflow> {

    Optional<Workflow> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Workflow> findByOrganizationIdAndVersionIdOrderByRelativePathAsc(UUID organizationId, UUID versionId);

    /**
     * Deletes every workflow of a version; the database removes the skill links to them.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of workflows deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Workflow x WHERE x.organizationId = :organizationId AND x.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes the workflow at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path
     * @return the number of workflows deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Workflow x WHERE x.organizationId = :organizationId AND x.versionId = :versionId"
            + " AND x.relativePath = :relativePath")
    int deleteByPath(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("relativePath") String relativePath);

    /**
     * Deletes the given parts of a version; a row with a path is a delivered file and is kept.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param partIds        the part ids, not empty
     * @return the number of parts deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Workflow x WHERE x.organizationId = :organizationId AND x.versionId = :versionId"
            + " AND x.id IN :partIds"
            + " AND x.relativePath IS NULL")
    int deleteParts(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("partIds") Collection<UUID> partIds);
}
