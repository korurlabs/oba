package com.korur.oba.apps.kaya.reference.repository;

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
import com.korur.oba.apps.kaya.reference.entity.Reference;

/**
 * Reads and writes the {@link Reference} rows of the reference domain.
 */
@Repository
public interface ReferenceRepository extends JpaRepository<Reference, UUID>, JpaSpecificationExecutor<Reference> {

    Optional<Reference> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Reference> findByOrganizationIdAndVersionIdOrderByNameAscIdAsc(UUID organizationId, UUID versionId);

    /**
     * Deletes every reference of a version; the database removes the skill links to them.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of references deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Reference r WHERE r.organizationId = :organizationId AND r.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes the given parts of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param partIds        the part ids, not empty
     * @return the number of parts deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Reference r WHERE r.organizationId = :organizationId AND r.versionId = :versionId"
            + " AND r.id IN :partIds")
    int deleteParts(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("partIds") Collection<UUID> partIds);
}
