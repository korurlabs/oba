package com.korur.oba.apps.kaya.release.repository;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.entity.Release;

/**
 * Signed releases of repositories. Numbers grow per repository from 1 under
 * {@link #lockReleases}, and a release is never changed once inserted.
 */
@Repository
public interface ReleaseRepository extends JpaRepository<Release, UUID>,
        JpaSpecificationExecutor<Release> {

    Page<Release> findByRepositoryId(UUID repositoryId, Pageable pageable);

    Optional<Release> findByRepositoryIdAndNumber(UUID repositoryId, Integer number);

    Optional<Release> findFirstByRepositoryIdOrderByNumberDesc(UUID repositoryId);

    /**
     * Whether a release of one of the repositories delivers content with this hash, read with
     * jsonb containment on the manifest so no manifest is loaded.
     *
     * @param repositoryIds the repositories, not empty
     * @param sha256        the content hash
     * @return true when at least one release names it
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM kaya.releases r
                WHERE r.repository_id IN (:repositoryIds)
                  AND r.manifest @> jsonb_build_array(jsonb_build_object('sha256', CAST(:sha256 AS text))))
            """, nativeQuery = true)
    boolean existsDeliveringSha256(@Param("repositoryIds") Collection<UUID> repositoryIds, @Param("sha256") String sha256);

    @Query("SELECT COALESCE(MAX(r.number), 0) FROM Release r WHERE r.repositoryId = :repositoryId")
    int findMaxNumber(@Param("repositoryId") UUID repositoryId);

    /**
     * Holds a transaction-scoped advisory lock on the repository's releases until commit, so two
     * releases of one repository are numbered one after the other. A row lock cannot do this: a
     * repository without a release has no release row to lock.
     *
     * @param lockKey the repository id as text
     * @return always 1
     */
    @Query(value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) AS release_lock",
            nativeQuery = true)
    long lockReleases(@Param("lockKey") String lockKey);

    /**
     * Holds the transaction-scoped advisory lock of a pack or project, the one an approval of its
     * versions takes, so a pack order change and an approval of a touched pack cannot release one
     * repository from two different compositions.
     *
     * @param lockKey the pack or project id as text
     * @return always 1
     */
    @Query(value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) AS owner_lock",
            nativeQuery = true)
    long lockOwner(@Param("lockKey") String lockKey);

    /**
     * Inserts a release with the creation time the signature already covers. Saving through JPA
     * would let auditing pick its own creation time after the document was signed.
     *
     * @return the number of inserted rows, 1
     */
    @Modifying
    @Query(value = """
            INSERT INTO kaya.releases
                (id, repository_id, number, cause_version_id, manifest, pack_versions, manifest_sha256, signature, key_id,
                 created_at, updated_at)
            VALUES (:id, :repositoryId, :number, :causeVersionId, CAST(:manifest AS jsonb), CAST(:packVersions AS jsonb),
                    :manifestSha256, :signature, :keyId, :createdAt, :createdAt)
            """, nativeQuery = true)
    int insertSigned(@Param("id") UUID id,
                     @Param("repositoryId") UUID repositoryId,
                     @Param("number") int number,
                     @Param("causeVersionId") UUID causeVersionId,
                     @Param("manifest") String manifest,
                     @Param("packVersions") String packVersions,
                     @Param("manifestSha256") String manifestSha256,
                     @Param("signature") String signature,
                     @Param("keyId") String keyId,
                     @Param("createdAt") Instant createdAt);
}
