package com.korur.oba.apps.kaya.plan.repository;

// 1. Framework-level
import org.springframework.data.domain.Limit;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.entity.PlanFeedback;
import com.korur.oba.apps.kaya.plan.enums.PlanFeedbackDecision;

@Repository
public interface PlanFeedbackRepository extends JpaRepository<PlanFeedback, UUID>, JpaSpecificationExecutor<PlanFeedback> {

    Optional<PlanFeedback> findByIdAndPlanId(UUID id, UUID planId);

    /** The feedback of a plan in the order it was given, as the plan page lists it. */
    List<PlanFeedback> findByPlanIdOrderByCreatedAtAscIdAsc(UUID planId);

    /**
     * The newest feedback of the given decisions on a version, which decides the review state
     * once the deciding feedback is withdrawn.
     */
    Optional<PlanFeedback> findFirstByVersionIdAndDecisionInOrderByCreatedAtDescIdDesc(UUID versionId,
            Collection<PlanFeedbackDecision> decisions);

    /**
     * The feedback of the plans that no session has received yet, oldest first, as the CLI delivers
     * it. Served by the partial index on undelivered rows.
     */
    List<PlanFeedback> findByPlanIdInAndDeliveredAtIsNullOrderByCreatedAtAscIdAsc(Collection<UUID> planIds, Limit limit);

    /** The plans of the given feedback rows that still wait for delivery. */
    @Query("SELECT DISTINCT f.planId FROM PlanFeedback f WHERE f.id IN :ids AND f.deliveredAt IS NULL")
    List<UUID> findPlanIdsOfPending(@Param("ids") Collection<UUID> ids);

    /**
     * Marks the given feedback rows of one plan delivered to a session, in one statement, skipping
     * rows already delivered; so a resent acknowledgement changes nothing and its count is zero.
     *
     * @param planId      the plan the rows must belong to
     * @param ids         the feedback rows
     * @param sessionId   the Claude Code session id of the receiving session
     * @param deliveredAt the time of delivery
     * @return the number of rows marked now
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PlanFeedback f
            SET f.deliveredAt = :deliveredAt, f.deliveredSessionId = :sessionId, f.updatedAt = :deliveredAt
            WHERE f.planId = :planId AND f.id IN :ids AND f.deliveredAt IS NULL
            """)
    int markDelivered(@Param("planId") UUID planId, @Param("ids") Collection<UUID> ids,
            @Param("sessionId") String sessionId, @Param("deliveredAt") Instant deliveredAt);
}
