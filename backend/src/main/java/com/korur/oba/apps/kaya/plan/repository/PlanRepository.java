package com.korur.oba.apps.kaya.plan.repository;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.entity.Plan;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID>, JpaSpecificationExecutor<Plan> {

    Optional<Plan> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndPackId(UUID organizationId, UUID packId);

    /** The SPEC plan of a repository path; the partial unique index allows at most one. */
    Optional<Plan> findByRepositoryIdAndKindAndPath(UUID repositoryId, PlanKind kind, String path);

    /** The SESSION plan of a session; the partial unique index allows at most one. */
    Optional<Plan> findBySessionIdAndKind(UUID sessionId, PlanKind kind);

    /**
     * Loads a plan of the organization with a row lock held until the transaction ends, so a new
     * version and a decision on the plan run one after the other and a decision never lands on a
     * version that was just replaced.
     *
     * @param id             the plan id
     * @param organizationId the organization the plan must belong to
     * @return the locked plan, or empty when the organization has no such plan
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Plan p WHERE p.id = :id AND p.organizationId = :organizationId")
    Optional<Plan> findByIdAndOrganizationIdForUpdate(@Param("id") UUID id,
            @Param("organizationId") UUID organizationId);

    /** The repositories that have plans, for the list's filter options. */
    @Query("SELECT DISTINCT p.repositoryId FROM Plan p WHERE p.organizationId = :organizationId")
    List<UUID> findDistinctRepositoryIds(@Param("organizationId") UUID organizationId);

    /** The packs that have plans, for the list's filter options. */
    @Query("SELECT DISTINCT p.packId FROM Plan p WHERE p.organizationId = :organizationId AND p.packId IS NOT NULL")
    List<UUID> findDistinctPackIds(@Param("organizationId") UUID organizationId);

    /** The users of latest versions, for the list's filter options. */
    @Query("SELECT DISTINCT p.userId FROM Plan p WHERE p.organizationId = :organizationId")
    List<UUID> findDistinctUserIds(@Param("organizationId") UUID organizationId);

    /**
     * The plans of a user whose latest version came from a session and that hold feedback no
     * session has received yet: the plans whose feedback the user's CLI may deliver.
     */
    @Query("""
            SELECT p FROM Plan p
            WHERE p.organizationId = :organizationId AND p.userId = :userId AND p.latestSessionId IS NOT NULL
              AND EXISTS (SELECT f.id FROM PlanFeedback f WHERE f.planId = p.id AND f.deliveredAt IS NULL)
            """)
    List<Plan> findWithPendingFeedback(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);
}
