package com.korur.oba.apps.kaya.agentsfile.repository;

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
import com.korur.oba.apps.kaya.agentsfile.entity.AgentsFile;
import com.korur.oba.apps.kaya.agentsfile.enums.AgentsFilePlacement;

/**
 * Reads and writes the {@link AgentsFile} rows of the agents file domain.
 */
@Repository
public interface AgentsFileRepository extends JpaRepository<AgentsFile, UUID>, JpaSpecificationExecutor<AgentsFile> {

    Optional<AgentsFile> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<AgentsFile> findByOrganizationIdAndVersionIdOrderByPlacementAsc(UUID organizationId, UUID versionId);

    /**
     * Deletes every agents file of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of agents files deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AgentsFile a WHERE a.organizationId = :organizationId AND a.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes the agents file at one placement of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param placement      the placement
     * @return the number of agents files deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AgentsFile a WHERE a.organizationId = :organizationId AND a.versionId = :versionId"
            + " AND a.placement = :placement")
    int deleteByPlacement(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("placement") AgentsFilePlacement placement);
}
