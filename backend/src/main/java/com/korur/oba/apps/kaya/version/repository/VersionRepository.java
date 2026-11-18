package com.korur.oba.apps.kaya.version.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party
import jakarta.persistence.LockModeType;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.version.entity.Version;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;

/**
 * Reads and writes {@link Version} rows, always within one organization. The owner of a
 * version is always a pack, so a pack id names the versions of one pack.
 */
@Repository
public interface VersionRepository extends JpaRepository<Version, UUID>, JpaSpecificationExecutor<Version> {

    Optional<Version> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Version> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    List<Version> findByOrganizationIdAndPackIdOrderByNumberAsc(UUID organizationId, UUID packId);

    /**
     * The highest version number of a pack; 0 for an owner without versions.
     *
     * @param ownerId the pack id
     * @return the highest number
     */
    @Query("SELECT COALESCE(MAX(v.number), 0) FROM Version v WHERE v.packId = :ownerId")
    int findMaxNumber(@Param("ownerId") UUID ownerId);

    /**
     * The version of a pack in the given status, the first one found. With
     * {@link VersionStatus#IN_REVIEW} there is at most one, which a partial unique index enforces.
     *
     * @param ownerId the pack id
     * @param status  the status
     * @return the version, empty when there is none
     */
    @Query("SELECT v FROM Version v WHERE v.packId = :ownerId AND v.status = :status "
            + "ORDER BY v.number ASC LIMIT 1")
    Optional<Version> findFirstByOwnerAndStatus(@Param("ownerId") UUID ownerId, @Param("status") VersionStatus status);

    /**
     * Loads a version and locks its row until the caller's transaction ends, so that two writes to
     * one draft (two saves of one path, a save and a submit) run one after the other. The caller
     * checks the organization afterwards.
     *
     * @param id the version
     * @return the locked version, empty when there is none
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Version> findWithLockById(UUID id);

    /**
     * Takes the transaction-scoped advisory lock of a pack.
     *
     * Draft creation, restore and approval of one owner run one after the other under it, so the
     * next version number and the current-version check are never read by two writers at once.
     *
     * @param lockKey the owner id as text
     * @return always 1; the count only gives the query a result
     */
    @Query(value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) AS version_lock",
            nativeQuery = true)
    long lockOwner(@Param("lockKey") String lockKey);

    /**
     * The versions of a pack in the given statuses up to a number, lowest number first, for the
     * derived version number of an item.
     *
     * @param packId   the pack
     * @param statuses the statuses read
     * @param number   the highest number read
     * @return the versions
     */
    List<Version> findByPackIdAndStatusInAndNumberLessThanEqualOrderByNumberAsc(UUID packId,
                                                                                 Collection<VersionStatus> statuses,
                                                                                 Integer number);
}
