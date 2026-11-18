package com.korur.oba.apps.kaya.repository.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 2. Third-party
import jakarta.persistence.LockModeType;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.entity.Repository;

/**
 * Spring Data access to {@link Repository} rows, used only inside the repository domain.
 */
// The stereotype is written out in full because the entity of this domain is also named Repository.
@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, UUID>, JpaSpecificationExecutor<Repository> {

    Optional<Repository> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Repository> findByOrganizationIdAndRemoteUrl(UUID organizationId, String remoteUrl);

    List<Repository> findByOrganizationIdOrderByNameAscRemoteUrlAsc(UUID organizationId);

    /**
     * Loads a repository of the organization with a row lock held until the transaction ends.
     *
     * A pipeline replacement takes it, so two replacements of the same repository at the same
     * moment run one after the other instead of both inserting a first pipeline row.
     *
     * @param id             the repository id
     * @param organizationId the organization of the request
     * @return the locked repository, or empty when the organization has no such repository
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Repository r WHERE r.id = :id AND r.organizationId = :organizationId")
    Optional<Repository> findByIdAndOrganizationIdForUpdate(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);

    /**
     * Inserts a repository unless the organization already has one with this remote URL.
     *
     * Two recorders that first report the same repository at the same moment then both succeed:
     * one inserts and the other finds the row, instead of the second failing on the unique
     * constraint and aborting its transaction. The status takes its column default, ACTIVE.
     *
     * @param organizationId the organization of the ingest
     * @param name           the display name
     * @param remoteUrl      the normalised remote URL
     * @return 1 when the row was inserted, 0 when it already existed
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.repositories (organization_id, name, remote_url)
            VALUES (:organizationId, :name, :remoteUrl)
            ON CONFLICT (organization_id, remote_url) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("organizationId") UUID organizationId,
                       @Param("name") String name,
                       @Param("remoteUrl") String remoteUrl);
}
