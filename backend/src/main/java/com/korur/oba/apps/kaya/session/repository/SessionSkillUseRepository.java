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
import com.korur.oba.apps.kaya.session.entity.SessionSkillUse;

@Repository
public interface SessionSkillUseRepository extends JpaRepository<SessionSkillUse, UUID>, JpaSpecificationExecutor<SessionSkillUse> {

    /**
     * Writes the use count of a skill as counted over every event of the session, so the row holds
     * the total, never a sum of batches.
     *
     * @param source the SessionSkillUseSource name
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.session_skill_uses (session_id, skill, source, use_count, first_used_at)
            VALUES (:sessionRowId, :skill, CAST(:source AS session_session_skill_use_source_enum), :useCount, :firstUsedAt)
            ON CONFLICT (session_id, skill, source) DO UPDATE
            SET use_count = EXCLUDED.use_count,
                first_used_at = EXCLUDED.first_used_at,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(@Param("sessionRowId") UUID sessionRowId, @Param("skill") String skill, @Param("source") String source,
            @Param("useCount") int useCount, @Param("firstUsedAt") Instant firstUsedAt);

    List<SessionSkillUse> findBySession_IdOrderByFirstUsedAtAscSkillAsc(UUID sessionRowId);
}
