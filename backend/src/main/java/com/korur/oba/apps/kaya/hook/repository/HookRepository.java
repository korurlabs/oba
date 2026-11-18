package com.korur.oba.apps.kaya.hook.repository;

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
import com.korur.oba.apps.kaya.hook.entity.Hook;

/**
 * Reads and writes the {@link Hook} rows of the hook domain.
 */
@Repository
public interface HookRepository extends JpaRepository<Hook, UUID>, JpaSpecificationExecutor<Hook> {

    Optional<Hook> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Hook> findByOrganizationIdAndVersionIdOrderByRelativePathAsc(UUID organizationId, UUID versionId);

    /**
     * Deletes every hook of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of hooks deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Hook x WHERE x.organizationId = :organizationId AND x.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes the hook at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path
     * @return the number of hooks deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Hook x WHERE x.organizationId = :organizationId AND x.versionId = :versionId"
            + " AND x.relativePath = :relativePath")
    int deleteByPath(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("relativePath") String relativePath);
}
