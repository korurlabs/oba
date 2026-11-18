package com.korur.oba.apps.kaya.guardrail.repository;

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
import com.korur.oba.apps.kaya.guardrail.entity.Guardrail;

/**
 * Reads and writes the {@link Guardrail} rows of the guardrail domain.
 */
@Repository
public interface GuardrailRepository extends JpaRepository<Guardrail, UUID>, JpaSpecificationExecutor<Guardrail> {

    Optional<Guardrail> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Guardrail> findByOrganizationIdAndVersionIdOrderByNameAsc(UUID organizationId, UUID versionId);

    /**
     * Deletes every guardrail of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the number of guardrails deleted
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Guardrail g WHERE g.organizationId = :organizationId AND g.versionId = :versionId")
    int deleteByVersion(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId);

    /**
     * Deletes the guardrail of one name in a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param name           the file name without .md
     * @return the number of guardrails deleted, 0 or 1
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Guardrail g WHERE g.organizationId = :organizationId AND g.versionId = :versionId"
            + " AND g.name = :name")
    int deleteByName(@Param("organizationId") UUID organizationId, @Param("versionId") UUID versionId,
            @Param("name") String name);
}
