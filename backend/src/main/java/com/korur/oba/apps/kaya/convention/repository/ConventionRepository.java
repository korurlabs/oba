package com.korur.oba.apps.kaya.convention.repository;

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
import com.korur.oba.apps.kaya.convention.entity.Convention;

/**
 * Reads and writes the {@link Convention} rows of the convention domain.
 */
@Repository
public interface ConventionRepository extends JpaRepository<Convention, UUID>, JpaSpecificationExecutor<Convention> {

    Optional<Convention> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Convention> findByOrganizationIdAndVersionIdOrderByRelativePathAsc(UUID organizationId, UUID versionId);

    /**
     * Deletes every convention of a version; the database removes the skill links to them.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of conventions deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Convention c WHERE c.organizationId = :organizationId AND c.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes the convention at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path
     * @return the number of conventions deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Convention c WHERE c.organizationId = :organizationId AND c.versionId = :versionId"
            + " AND c.relativePath = :relativePath")
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
    @Query("DELETE FROM Convention c WHERE c.organizationId = :organizationId AND c.versionId = :versionId"
            + " AND c.id IN :partIds"
            + " AND c.relativePath IS NULL")
    int deleteParts(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("partIds") Collection<UUID> partIds);
}
