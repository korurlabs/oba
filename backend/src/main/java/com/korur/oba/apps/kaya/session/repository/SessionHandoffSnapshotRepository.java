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
import com.korur.oba.apps.kaya.session.entity.SessionHandoffSnapshot;

@Repository
public interface SessionHandoffSnapshotRepository
        extends JpaRepository<SessionHandoffSnapshot, UUID>, JpaSpecificationExecutor<SessionHandoffSnapshot> {

    /**
     * Keeps the newest capture of a handoff. A snapshot captured before the stored one is a
     * replay from an older batch and changes nothing.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.session_handoff_snapshots (session_id, path, content, captured_at)
            VALUES (:sessionRowId, :path, :content, :capturedAt)
            ON CONFLICT (session_id, path) DO UPDATE
            SET content = EXCLUDED.content,
                captured_at = EXCLUDED.captured_at,
                updated_at = CURRENT_TIMESTAMP
            WHERE kaya.session_handoff_snapshots.captured_at <= EXCLUDED.captured_at
            """, nativeQuery = true)
    int upsertIfNewer(@Param("sessionRowId") UUID sessionRowId, @Param("path") String path, @Param("content") String content,
            @Param("capturedAt") Instant capturedAt);

    List<SessionHandoffSnapshot> findBySession_Id(UUID sessionRowId);

    List<SessionHandoffSnapshot> findBySession_IdOrderByPathAsc(UUID sessionRowId);
}
