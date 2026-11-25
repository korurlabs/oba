package com.korur.oba.apps.kazan.cyberscore.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party
import jakarta.persistence.LockModeType;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;

/**
 * Repository interface for {@link Cyberscore} entity database operations.
 *
 * The row of an organization is also its scoring lock: a scoring write first
 * makes sure the row exists with {@link #insertIfAbsent(UUID)}, then locks it
 * with {@link #findByOrganizationIdForUpdate(UUID)}, so the scoring writes of
 * one organization run one after another and each recalculation sees the
 * checks the previous one wrote.
 */
@Repository
public interface CyberscoreRepository extends JpaRepository<Cyberscore, UUID>, JpaSpecificationExecutor<Cyberscore> {

    /**
     * Finds the score row of the organization without a lock, for reads.
     *
     * @param organizationId ID of the organization
     * @return the score row, or empty when the organization was never scored
     */
    Optional<Cyberscore> findByOrganizationId(UUID organizationId);

    /**
     * Loads the score row of the organization with a row lock held until the
     * transaction ends.
     *
     * @param organizationId ID of the organization
     * @return the locked row, or empty when it does not exist yet
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cyberscore c WHERE c.organizationId = :organizationId")
    Optional<Cyberscore> findByOrganizationIdForUpdate(@Param("organizationId") UUID organizationId);

    /**
     * Inserts the not-yet-scored row of the organization unless it already has one.
     *
     * Two first scoring writes of an organization at the same moment then both
     * succeed: one inserts and the other finds the row, instead of the second
     * failing on the unique constraint and aborting its transaction. The table
     * has no id default, so the id is generated here; every other column takes
     * its default.
     *
     * @param organizationId ID of the organization
     * @return 1 when the row was inserted, 0 when it already existed
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kazan.cyberscores (id, organization_id)
            VALUES (gen_random_uuid(), :organizationId)
            ON CONFLICT (organization_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("organizationId") UUID organizationId);
}
