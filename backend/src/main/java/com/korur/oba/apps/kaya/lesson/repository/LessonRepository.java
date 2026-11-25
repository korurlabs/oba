package com.korur.oba.apps.kaya.lesson.repository;

// 1. Framework-level
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryProjection;
import com.korur.oba.apps.kaya.lesson.dto.projection.LessonDeliveryStateProjection;
import com.korur.oba.apps.kaya.lesson.entity.Lesson;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID>, JpaSpecificationExecutor<Lesson> {

    Optional<Lesson> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** The repositories that have lessons, for the list's filter options. */
    @Query("SELECT DISTINCT l.repositoryId FROM Lesson l WHERE l.organizationId = :organizationId AND l.repositoryId IS NOT NULL")
    List<UUID> findDistinctRepositoryIds(@Param("organizationId") UUID organizationId);

    /** The authors of lessons, for the list's filter options. */
    @Query("SELECT DISTINCT l.userId FROM Lesson l WHERE l.organizationId = :organizationId")
    List<UUID> findDistinctUserIds(@Param("organizationId") UUID organizationId);

    /**
     * One page of the lessons a checkout reads, newest first with the id as tie-breaker and without
     * its body, so that the text of each lesson never travels at session start. The order is part of
     * the query, so the pageable passed in carries only the page and its size.
     *
     * @param organizationId the organization of the machine token
     * @param repositoryId   the repository of the checkout, or null when the remote is unknown
     * @param page           the page and its size, unsorted
     * @return the page of the lessons of the repository and the organization-wide ones
     */
    @Query("""
            SELECT l.id AS id, l.title AS title, l.repositoryId AS repositoryId, l.updatedAt AS updatedAt
            FROM Lesson l
            WHERE l.organizationId = :organizationId
              AND (l.repositoryId = :repositoryId OR l.repositoryId IS NULL)
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<LessonDeliveryProjection> findDeliveryLessons(@Param("organizationId") UUID organizationId,
            @Param("repositoryId") UUID repositoryId, Pageable page);

    /**
     * Counts the lessons a checkout reads and digests them in the database, so that the ETag of
     * every page costs one row on the wire instead of every lesson. The digest is the SHA-256 of one
     * line per lesson, its id and the epoch of its updatedAt, ordered by id: an add or a delete
     * changes the lines, and an edit or a move changes updatedAt. The epoch keeps the text the same
     * whatever time zone the connection uses.
     *
     * @param organizationId the organization of the machine token
     * @param repositoryId   the repository of the checkout, or null when the remote is unknown
     * @return the count and the 64 lower-case hex digest; an empty set digests the empty text
     */
    @Query(value = """
            SELECT COUNT(*) AS total,
                   encode(sha256(convert_to(COALESCE(string_agg(
                       CAST(l.id AS text) || ':' || CAST(extract(epoch FROM l.updated_at) AS text),
                       chr(10) ORDER BY l.id), ''), 'UTF8')), 'hex') AS digest
            FROM kaya.lessons l
            WHERE l.organization_id = :organizationId
              AND (l.repository_id = :repositoryId OR l.repository_id IS NULL)
            """, nativeQuery = true)
    LessonDeliveryStateProjection findDeliveryState(@Param("organizationId") UUID organizationId,
            @Param("repositoryId") UUID repositoryId);
}
