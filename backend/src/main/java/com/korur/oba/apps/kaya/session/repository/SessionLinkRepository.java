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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.entity.SessionLink;
import com.korur.oba.apps.kaya.session.enums.SessionLinkKind;

@Repository
public interface SessionLinkRepository extends JpaRepository<SessionLink, UUID>, JpaSpecificationExecutor<SessionLink> {

    /**
     * Adds a link the recorder found, unless the session already has it (from an earlier batch or
     * added by hand).
     *
     * @param kind the SessionLinkKind name
     * @return 1 when the link was added, 0 when the session had it
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.session_links (session_id, kind, reference, url, source)
            VALUES (:sessionRowId, CAST(:kind AS session_session_link_kind_enum), :reference, :url,
                    CAST('RECORDER' AS session_session_link_source_enum))
            ON CONFLICT (session_id, kind, reference) DO NOTHING
            """, nativeQuery = true)
    int insertRecorderLinkIfAbsent(@Param("sessionRowId") UUID sessionRowId, @Param("kind") String kind,
            @Param("reference") String reference, @Param("url") String url);

    /**
     * Adds a link a member gave, unless the session already has it. Two members adding the same link
     * at once both pass any read-first check, so the unique constraint decides here: the second
     * insert waits for the first to commit and then adds nothing.
     *
     * @param kind the SessionLinkKind name
     * @return 1 when the link was added, 0 when the session had it
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.session_links (session_id, kind, reference, url, source, created_by)
            VALUES (:sessionRowId, CAST(:kind AS session_session_link_kind_enum), :reference, :url,
                    CAST('MANUAL' AS session_session_link_source_enum), :createdBy)
            ON CONFLICT (session_id, kind, reference) DO NOTHING
            """, nativeQuery = true)
    int insertManualLinkIfAbsent(@Param("sessionRowId") UUID sessionRowId, @Param("kind") String kind,
            @Param("reference") String reference, @Param("url") String url, @Param("createdBy") UUID createdBy);

    List<SessionLink> findBySession_IdOrderByKindAscReferenceAsc(UUID sessionRowId);

    List<SessionLink> findBySession_IdInOrderByKindAscReferenceAsc(Collection<UUID> sessionRowIds);

    Optional<SessionLink> findByIdAndSession_Id(UUID id, UUID sessionRowId);

    Optional<SessionLink> findBySession_IdAndKindAndReference(UUID sessionRowId, SessionLinkKind kind, String reference);
}
