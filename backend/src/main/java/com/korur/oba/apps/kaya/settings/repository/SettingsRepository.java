package com.korur.oba.apps.kaya.settings.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.settings.entity.Settings;

/**
 * Reads and writes the {@link Settings} rows of the settings domain.
 */
@Repository
public interface SettingsRepository extends JpaRepository<Settings, UUID>, JpaSpecificationExecutor<Settings> {

    Optional<Settings> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Settings> findByOrganizationIdAndVersionId(UUID organizationId, UUID versionId);

    /**
     * Deletes the settings of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of rows deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Settings x WHERE x.organizationId = :organizationId AND x.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);
}
