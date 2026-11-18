package com.korur.oba.apps.kaya.session.repository;

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
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.dto.projection.SessionEventAggregateProjection;
import com.korur.oba.apps.kaya.session.entity.SessionEvent;

@Repository
public interface SessionEventRepository extends JpaRepository<SessionEvent, UUID>, JpaSpecificationExecutor<SessionEvent> {

    /**
     * Stores the events of one batch in one statement. A seq the session already holds is skipped,
     * so a batch the sender replays after a lost acknowledgement stores each event once.
     *
     * @param sessionRowId     the session, locked by the caller
     * @param eventsJson a JSON array of {seq, at, event, agent_id, payload}
     * @return the number of events stored
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.session_events (session_id, seq, at, event, agent_id, payload)
            SELECT :sessionRowId, e.seq, e.at, e.event, e.agent_id, e.payload
            FROM jsonb_to_recordset(CAST(:eventsJson AS jsonb))
                 AS e(seq BIGINT, at TIMESTAMPTZ, event VARCHAR(40), agent_id VARCHAR(100), payload JSONB)
            ON CONFLICT (session_id, seq) DO NOTHING
            """, nativeQuery = true)
    int insertAll(@Param("sessionRowId") UUID sessionRowId, @Param("eventsJson") String eventsJson);

    List<SessionEvent> findBySession_IdOrderBySeqAsc(UUID sessionRowId);

    @Query(value = """
            SELECT COUNT(*) AS eventCount,
                   COALESCE(MAX(e.seq), 0) AS maxSeq,
                   MIN(e.at) AS firstAt,
                   MAX(e.at) AS lastAt
            FROM kaya.session_events e
            WHERE e.session_id = :sessionRowId
            """, nativeQuery = true)
    SessionEventAggregateProjection aggregateBySessionRowId(@Param("sessionRowId") UUID sessionRowId);

    /**
     * The highest seq n for which the session holds every seq from 1 to n; 0 when it lacks seq 1.
     * Seqs are unique per session and start at 1, so a seq equals its position in seq order exactly
     * until the first gap.
     *
     * @param sessionRowId the session
     * @return the highest contiguous seq
     */
    @Query(value = """
            SELECT COALESCE(MAX(n.seq), 0)
            FROM (SELECT e.seq, ROW_NUMBER() OVER (ORDER BY e.seq) AS position
                  FROM kaya.session_events e
                  WHERE e.session_id = :sessionRowId) n
            WHERE n.seq = n.position
            """, nativeQuery = true)
    long findContiguousSeq(@Param("sessionRowId") UUID sessionRowId);

    List<SessionEvent> findBySession_IdAndSeqGreaterThanOrderBySeqAsc(UUID sessionRowId, long seq);
}
