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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.entity.SessionSubagent;

@Repository
public interface SessionSubagentRepository extends JpaRepository<SessionSubagent, UUID>, JpaSpecificationExecutor<SessionSubagent> {

    /**
     * Writes a subagent as the events derive it now. A model, description or stop time the
     * events no longer carry is kept, because a later batch never holds less than an earlier one.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.session_subagents (session_id, agent_id, subagent_type, model, description, started_at, ended_at)
            VALUES (:sessionRowId, :agentId, :subagentType, :model, :description, :startedAt, :endedAt)
            ON CONFLICT (session_id, agent_id) DO UPDATE
            SET subagent_type = EXCLUDED.subagent_type,
                model = COALESCE(EXCLUDED.model, kaya.session_subagents.model),
                description = COALESCE(EXCLUDED.description, kaya.session_subagents.description),
                started_at = EXCLUDED.started_at,
                ended_at = COALESCE(EXCLUDED.ended_at, kaya.session_subagents.ended_at),
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(@Param("sessionRowId") UUID sessionRowId, @Param("agentId") String agentId,
            @Param("subagentType") String subagentType, @Param("model") String model,
            @Param("description") String description, @Param("startedAt") Instant startedAt,
            @Param("endedAt") Instant endedAt);

    List<SessionSubagent> findBySession_IdOrderByStartedAtAscAgentIdAsc(UUID sessionRowId);
}
