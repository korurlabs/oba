package com.korur.oba.apps.kaya.machine.repository;

// 1. Framework-level
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.projection.MachineAccountSummaryProjection;
import com.korur.oba.apps.kaya.machine.dto.projection.MachineUserSummaryProjection;
import com.korur.oba.apps.kaya.machine.entity.Machine;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;

@Repository
public interface MachineRepository extends JpaRepository<Machine, UUID>, JpaSpecificationExecutor<Machine> {

    @EntityGraph(attributePaths = {"user", "account"})
    Optional<Machine> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Override
    @EntityGraph(attributePaths = {"user", "account"})
    List<Machine> findAll(Specification<Machine> specification, Sort sort);

    Optional<Machine> findByOrganizationIdAndUser_IdAndMachineName(UUID organizationId, UUID userId, String machineName);

    /**
     * Loads a machine of the organization with a row lock held until the transaction ends, so a
     * rotation, a revocation, a retirement and a revoke all of its user run one after the other.
     * The user is not fetched here, because a join would lock the user row as well.
     *
     * @param id             the machine id
     * @param organizationId the organization the machine must belong to
     * @return the locked machine, or empty when the organization has no such machine
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Machine a WHERE a.id = :id AND a.organizationId = :organizationId")
    Optional<Machine> findByIdAndOrganizationIdForUpdate(@Param("id") UUID id,
            @Param("organizationId") UUID organizationId);

    /**
     * Locks every machine of a user in one organization, in id order so two callers never wait on each other in a
     * cycle. Revoke all takes these locks, so a token rotated at the same moment is either seen
     * by it or issued after it to the machine's new state.
     *
     * @param organizationId the organization whose machines are locked
     * @param userId         the user, already locked by the caller
     * @return the locked machines
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Machine a WHERE a.organizationId = :organizationId AND a.user.id = :userId ORDER BY a.id")
    List<Machine> findByOrganizationIdAndUserIdForUpdate(@Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId);

    /**
     * Counts the machines of each given user and finds when one of them was last seen.
     *
     * @param organizationId the organization of the users
     * @param userIds        the users, not empty
     * @return one row per user that has at least one machine
     */
    @Query(value = """
            SELECT a.user_id AS userId,
                   COUNT(*) AS machines,
                   COUNT(*) FILTER (WHERE a.status = 'ACTIVE') AS activeMachines,
                   MAX(a.last_seen_at) AS lastSeenAt
            FROM kaya.machines a
            WHERE a.organization_id = :organizationId
              AND a.user_id IN (:userIds)
            GROUP BY a.user_id
            """, nativeQuery = true)
    List<MachineUserSummaryProjection> summarizeByUserIds(@Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds);

    /**
     * Counts the machines whose latest account is each given account.
     *
     * @param organizationId the organization of the accounts
     * @param accountIds     the accounts, not empty
     * @return one row per account that has at least one machine
     */
    @Query(value = """
            SELECT a.account_id AS accountId,
                   COUNT(*) AS machines,
                   COUNT(*) FILTER (WHERE a.status = 'ACTIVE') AS activeMachines
            FROM kaya.machines a
            WHERE a.organization_id = :organizationId
              AND a.account_id IN (:accountIds)
            GROUP BY a.account_id
            """, nativeQuery = true)
    List<MachineAccountSummaryProjection> summarizeByAccountIds(@Param("organizationId") UUID organizationId,
            @Param("accountIds") Collection<UUID> accountIds);

    /**
     * Retires every ACTIVE machine of a user in one organization.
     *
     * @param organizationId the organization whose machines are retired
     * @param userId         the user, whose machines the caller has locked
     * @return the number of machines retired
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kaya.machines
            SET status = 'RETIRED', updated_at = CURRENT_TIMESTAMP
            WHERE organization_id = :organizationId
              AND user_id = :userId
              AND status = 'ACTIVE'
            """, nativeQuery = true)
    int retireActiveByOrganizationIdAndUserId(@Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId);


    /**
     * The machines of the organization in a status whose latest account is one of the given,
     * so machines sharing an account show the same usage.
     */
    List<Machine> findByOrganizationIdAndStatusAndAccount_IdIn(UUID organizationId, MachineStatus status,
            Collection<UUID> accountIds);
}
