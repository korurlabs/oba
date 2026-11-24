package com.korur.oba.apps.kalkan.page.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.dto.projection.KalkanPageRevisionListProjection;
import com.korur.oba.apps.kalkan.page.entity.KalkanPageRevision;

/**
 * Repository of {@link KalkanPageRevision}. Revisions are only inserted.
 */
@Repository
public interface KalkanPageRevisionRepository extends JpaRepository<KalkanPageRevision, UUID> {

    /**
     * Lists the revisions of a page without their bodies, which may be long and are read one at a
     * time.
     *
     * @param pageId the page
     * @return the revisions, newest page version first, then by locale
     */
    @Query("""
            SELECT r.id AS id, r.locale AS locale, r.title AS title, r.pageVersion AS pageVersion,
                   r.createdAt AS createdAt, r.createdById AS createdById
            FROM KalkanPageRevision r
            WHERE r.pageId = :pageId
            ORDER BY r.pageVersion DESC, r.createdAt DESC, r.locale ASC
            """)
    List<KalkanPageRevisionListProjection> findListByPageId(@Param("pageId") UUID pageId);

    /**
     * Finds a revision of a page.
     *
     * @param id     the revision id
     * @param pageId the page
     * @return the revision; empty when the page has no such revision
     */
    Optional<KalkanPageRevision> findByIdAndPageId(UUID id, UUID pageId);
}
