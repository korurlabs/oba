package com.korur.oba.apps.kaya.session.repository;

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
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.dto.projection.SessionCountProjection;
import com.korur.oba.apps.kaya.session.dto.projection.SessionMachineUserProjection;
import com.korur.oba.apps.kaya.session.dto.projection.SessionSubscriptionProjection;
import com.korur.oba.apps.kaya.session.entity.Session;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID>, JpaSpecificationExecutor<Session> {

    /**
     * Creates the session row of a session unless the organization already has one. A unique
     * violation would abort the whole ingest transaction, so a concurrent first batch of the same
     * session is absorbed by the conflict clause instead. features is written as an empty array
     * explicitly so the row never depends on the column default for the entity's list.
     *
     * @return 1 when the row was created, 0 when it already existed
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.sessions (organization_id, repository_id, user_id, machine_id, session_id, title,
                                    started_at, last_event_at, features)
            VALUES (:organizationId, :repositoryId, :userId, :machineId, :sessionId, '', :startedAt, :startedAt,
                    CAST('[]' AS jsonb))
            ON CONFLICT (organization_id, session_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("organizationId") UUID organizationId, @Param("repositoryId") UUID repositoryId,
            @Param("userId") UUID userId, @Param("machineId") UUID machineId, @Param("sessionId") String sessionId,
            @Param("startedAt") Instant startedAt);

    /**
     * Loads the session row of a Claude Code session with a row lock held until the transaction ends, so two
     * batches of one session apply one after the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Session c WHERE c.organizationId = :organizationId AND c.sessionId = :sessionId")
    Optional<Session> findByOrganizationIdAndSessionIdForUpdate(@Param("organizationId") UUID organizationId,
            @Param("sessionId") String sessionId);


    Optional<Session> findByOrganizationIdAndSessionId(UUID organizationId, String sessionId);

    List<Session> findByOrganizationIdAndSessionIdIn(UUID organizationId, Collection<String> sessionIds);

    /**
     * Counts the sessions of each given user and finds the last event of the newest one.
     *
     * @return one row per user with sessions; a user without sessions has none
     */
    @Query(value = """
            SELECT c.user_id AS id, COUNT(*) AS sessions, MAX(c.last_event_at) AS lastEventAt
            FROM kaya.sessions c
            WHERE c.organization_id = :organizationId
              AND c.user_id IN (:userIds)
            GROUP BY c.user_id
            """, nativeQuery = true)
    List<SessionCountProjection> countByUserIds(@Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds);

    /**
     * Counts the sessions of each given machine and finds the last event of the newest one.
     *
     * @return one row per machine with sessions; a machine without sessions has none
     */
    @Query(value = """
            SELECT c.machine_id AS id, COUNT(*) AS sessions, MAX(c.last_event_at) AS lastEventAt
            FROM kaya.sessions c
            WHERE c.organization_id = :organizationId
              AND c.machine_id IN (:machineIds)
            GROUP BY c.machine_id
            """, nativeQuery = true)
    List<SessionCountProjection> countByMachineIds(@Param("organizationId") UUID organizationId,
            @Param("machineIds") Collection<UUID> machineIds);

    @Query(value = "SELECT DISTINCT c.user_id FROM kaya.sessions c WHERE c.organization_id = :organizationId",
            nativeQuery = true)
    List<UUID> findDistinctUserIds(@Param("organizationId") UUID organizationId);

    @Query(value = """
            SELECT DISTINCT c.machine_id AS machineId, c.user_id AS userId
            FROM kaya.sessions c
            WHERE c.organization_id = :organizationId
            """, nativeQuery = true)
    List<SessionMachineUserProjection> findDistinctMachineUsers(@Param("organizationId") UUID organizationId);

    @Query(value = """
            SELECT DISTINCT c.model
            FROM kaya.sessions c
            WHERE c.organization_id = :organizationId
              AND c.model IS NOT NULL
            ORDER BY c.model
            """, nativeQuery = true)
    List<String> findDistinctModels(@Param("organizationId") UUID organizationId);

    /**
     * The Claude organizations the sessions ran on, each with the name, billing type and seat tier
     * of its newest session, since a later seat change shows on new sessions only.
     */
    @Query(value = """
            SELECT s.uuid, s.name, s.billingType, s.seatTier
            FROM (SELECT DISTINCT ON (c.account_organization_uuid)
                         c.account_organization_uuid AS uuid,
                         c.account_organization_name AS name,
                         c.billing_type AS billingType,
                         c.seat_tier AS seatTier
                  FROM kaya.sessions c
                  WHERE c.organization_id = :organizationId
                    AND c.account_organization_uuid IS NOT NULL
                  ORDER BY c.account_organization_uuid, c.last_event_at DESC) s
            ORDER BY s.name NULLS LAST, s.uuid
            """, nativeQuery = true)
    List<SessionSubscriptionProjection> findSubscriptions(@Param("organizationId") UUID organizationId);

    /**
     * Deletes every session whose last event is older than its organization's retention_days in
     * kaya.organization_settings, or 180 days, the column's default, for an organization without a
     * row. The child rows go with it by ON DELETE CASCADE; user audit entries do not reference
     * sessions and are never touched. The retention is read in the same statement, so a session is judged
     * by the setting at the moment it is deleted.
     *
     * @param now the time the retention is measured from
     * @return the number of sessions deleted
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM kaya.sessions c
            WHERE c.last_event_at < CAST(:now AS timestamptz) - make_interval(days => COALESCE(
                (SELECT s.retention_days FROM kaya.organization_settings s WHERE s.organization_id = c.organization_id),
                180))
            """, nativeQuery = true)
    int deleteExpired(@Param("now") Instant now);
}
