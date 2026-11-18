package com.korur.oba.apps.kaya.toollink.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
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
import com.korur.oba.apps.kaya.toollink.entity.ToolLink;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * Reads and writes the {@link ToolLink} rows of the tool link domain.
 */
@Repository
public interface ToolLinkRepository extends JpaRepository<ToolLink, UUID> {

    List<ToolLink> findByOrganizationIdAndVersionIdOrderByNameAsc(UUID organizationId, UUID versionId);

    List<ToolLink> findByOrganizationIdAndVersionIdIn(UUID organizationId, Collection<UUID> versionIds);

    Optional<ToolLink> findByOrganizationIdAndVersionIdAndName(UUID organizationId, UUID versionId, ToolLinkName name);

    /**
     * Deletes one tool link of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param name           the link
     * @return the number of rows deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ToolLink x WHERE x.organizationId = :organizationId AND x.versionId = :versionId AND x.name = :name")
    int deleteByName(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
                     @Param("name") ToolLinkName name);

    /**
     * Deletes every tool link of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of rows deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ToolLink x WHERE x.organizationId = :organizationId AND x.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);
}
